package de.hpi.bpt.scylla;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import de.hpi.bpt.scylla.model.global.CostVariantConfiguration;
import de.hpi.bpt.scylla.parser.*;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;

import static de.hpi.bpt.scylla.Scylla.*;

import de.hpi.bpt.scylla.exception.ScyllaValidationException;
import de.hpi.bpt.scylla.logger.DebugLogger;
import de.hpi.bpt.scylla.model.configuration.SimulationConfiguration;
import de.hpi.bpt.scylla.model.global.GlobalConfiguration;
import de.hpi.bpt.scylla.model.process.CommonProcessElements;
import de.hpi.bpt.scylla.model.process.ProcessModel;
import de.hpi.bpt.scylla.plugin.batch.BatchPluginUtils;
import de.hpi.bpt.scylla.plugin_loader.DependencyGraph.CycleException;
import de.hpi.bpt.scylla.plugin_loader.PluginLoader;
import de.hpi.bpt.scylla.plugin_type.logger.OutputLoggerPluggable;
import de.hpi.bpt.scylla.plugin_type.parser.CommonProcessElementsParserPluggable;
import de.hpi.bpt.scylla.plugin_type.parser.GlobalConfigurationParserPluggable;
import de.hpi.bpt.scylla.plugin_type.parser.ProcessModelParserPluggable;
import de.hpi.bpt.scylla.plugin_type.parser.SimulationConfigurationParserPluggable;
import de.hpi.bpt.scylla.simulation.SimulationModel;
import de.hpi.bpt.scylla.simulation.utils.DateTimeUtils;
import desmoj.core.simulator.Experiment;
import desmoj.core.simulator.TimeInstant;

/**
 * Simulation manager which controls the overall process of simulation, from input parsing to output logging.
 * 
 * @author Tsun Yin Wong
 *
 */
public class SimulationManager {

    private String experimentOutputFolder;

    private GlobalConfiguration globalConfiguration;

    private CostVariantConfiguration costVariantConfiguration;
    private Map<String, CommonProcessElements> commonProcessElements = new HashMap<String, CommonProcessElements>();
    private Map<String, ProcessModel> processModels = new HashMap<String, ProcessModel>();
    private Map<String, SimulationConfiguration> simulationConfigurations = new HashMap<String, SimulationConfiguration>();

    private boolean enableBpsLogging;
    private boolean enableDesLogging;

    private String[] processModelFilenames;
    private String[] simulationConfigurationFilenames;
    private String globalConfigurationFilename;

    private String costVariantConfigFile;
    
	private String outputPath;

    /**
     * Constructor.
     * 
     * @param folder
     *            location of files
     * @param processModelFilenames
     *            process model filenames in given folder
     * @param simulationConfigurationFilenames
     *            simulation configuration filenames in given folder
     * @param globalConfigurationFilename
     *            global configuration filename in given folder
     * @param enableBpsLogging
     *            log {@link de.hpi.bpt.scylla.logger.ProcessNodeInfo} objects if true
     * @param enableDesLogging
     *            log DesmoJ traces and write HTML trace file if true
     */
    public SimulationManager(String folder, String[] processModelFilenames, String[] simulationConfigurationFilenames,
            String globalConfigurationFilename, String costVariantConfigFile, boolean enableBpsLogging, boolean enableDesLogging) {
        // simulation manager including a cost variant configuration
        this.experimentOutputFolder = normalizePath(folder);
        this.processModelFilenames = normalizePaths(processModelFilenames);
        this.simulationConfigurationFilenames = normalizePaths(simulationConfigurationFilenames);
        this.globalConfigurationFilename = normalizePath(globalConfigurationFilename);
        this.costVariantConfigFile = normalizePath(costVariantConfigFile);
        this.enableBpsLogging = enableBpsLogging;
        this.enableDesLogging = enableDesLogging;
    }

    public SimulationManager(String folder, String[] processModelFilenames, String[] simulationConfigurationFilenames,
                             String globalConfigurationFilename, boolean enableBpsLogging, boolean enableDesLogging) {
        // simulation manager excluding a cost variant configuration
        this.experimentOutputFolder = normalizePath(folder);
        this.processModelFilenames = normalizePaths(processModelFilenames);
        this.simulationConfigurationFilenames = normalizePaths(simulationConfigurationFilenames);
        this.globalConfigurationFilename = normalizePath(globalConfigurationFilename);
        this.enableBpsLogging = enableBpsLogging;
        this.enableDesLogging = enableDesLogging;
    }

    /**
     * parses input, runs DesmoJ simulation experiment, writes BPS output logs
     */
    public String run() {
    	
    	Instant startTime = Instant.now();
    	
    	try {
			PluginLoader.getDefaultPluginLoader().prepareForSimulation();
		} catch (CycleException e) {
            DebugLogger.error(e.getMessage());
			e.printStackTrace();
			throw new Error(e);
		}

        try {
        	parseInput();
        }
        catch (JDOMException | IOException | ScyllaValidationException e) {
            DebugLogger.error(e.getMessage());
            e.printStackTrace();
            throw new Error(e);//TODO
        }

        // TODO validate resources in process models (i.e. check if they are all covered in resource data)

        TimeUnit epsilon = TimeUnit.SECONDS;
        DateTimeUtils.setReferenceTimeUnit(epsilon);

        String experimentName = Long.toString((new Date()).getTime());
        Experiment.setEpsilon(epsilon);
        Experiment exp = new Experiment(experimentName, enableDesLogging);
        exp.setShowProgressBar(false);

        // XXX each simulation configuration may have its own seed
        Long randomSeed = globalConfiguration.getRandomSeed();
        if (randomSeed != null) {
            exp.setSeedGenerator(randomSeed);
        }
        else {
            exp.setSeedGenerator((new Random()).nextLong());
        }

        SimulationModel sm = new SimulationModel(null, globalConfiguration, costVariantConfiguration, commonProcessElements, processModels,
                simulationConfigurations, enableBpsLogging, enableDesLogging);
        sm.connectToExperiment(exp);

        int lambda = 1;

        if (sm.getEndDateTime() != null) {
            // have to use time which is slightly after intended end time (epsilon)
            // otherwise the AbortProcessSimulationEvent(s) may not fire
            long simulationDuration = DateTimeUtils.getDuration(sm.getStartDateTime(), sm.getEndDateTime());
            TimeInstant simulationTimeInstant = new TimeInstant(simulationDuration + lambda, epsilon);
            exp.stop(simulationTimeInstant);
            exp.tracePeriod(new TimeInstant(0), simulationTimeInstant);
            exp.debugPeriod(new TimeInstant(0), simulationTimeInstant);
        }
        else {
            exp.traceOn(new TimeInstant(0));
            exp.debugOn(new TimeInstant(0));
        }

        if (!enableDesLogging) {
            exp.debugOff(new TimeInstant(0));
            exp.traceOff(new TimeInstant(0));
        }

        exp.start();
        exp.report();
        exp.finish();
       
           
        try {

            // log process execution
            // log resources, process, tasks
        	if(Objects.isNull(outputPath)) {
            	String currentTime = new SimpleDateFormat("yy_MM_dd_HH_mm_ss_SSS").format(new Date());
                StringBuilder strb = new StringBuilder(globalConfigurationFilename);
                strb
                	.delete(strb.lastIndexOf(Scylla.FILEDELIM)+1,strb.length())
                	.append("output_")
                	.append(currentTime);
                outputPath = strb.toString()+Scylla.FILEDELIM;
        	}
            File outputPathFolder = new File(outputPath);
            if(outputPathFolder.exists()) throw new Error("Output already exists!");
            outputPathFolder.mkdirs();
            assert outputPathFolder.exists();
            OutputLoggerPluggable.runPlugins(sm, outputPath);

        }
        catch (IOException e) {
            e.printStackTrace();
        } finally {
        	cleanup();
        }
        
    	Instant endTime = Instant.now();
    	Duration timeElapsed = Duration.between(startTime, endTime);
    	System.out.println(formatSimulationTime(timeElapsed));
        
        return outputPath;
    }
    
    protected String formatSimulationTime(Duration timeElapsed) {
    	StringBuilder sb = new StringBuilder("Total simulation time:");
    	if(timeElapsed.toDaysPart() > 0) sb.append(" ").append(timeElapsed.toDaysPart()).append(" days");
    	if(timeElapsed.toHoursPart() > 0) sb.append(" ").append(timeElapsed.toHoursPart()).append(" hours");
    	if(timeElapsed.toMinutesPart() > 0) sb.append(" ").append(timeElapsed.toMinutesPart()).append(" mins");
    	if(timeElapsed.toSecondsPart() > 0) sb.append(" ").append(timeElapsed.toSecondsPart()).append(" secs");
    	if(timeElapsed.toMillisPart() > 0) sb.append(" ").append(timeElapsed.toMillisPart()).append(" ms");
    	
    	return sb.toString();
    }
    
    protected void parseInput() throws ScyllaValidationException, JDOMException, IOException {
        SAXBuilder builder = new SAXBuilder();
        
        if (globalConfigurationFilename == null || globalConfigurationFilename.isEmpty())
            throw new ScyllaValidationException("No global configuration provided.");

        if (costVariantConfigFile == null || costVariantConfigFile.isEmpty())
            throw new ScyllaValidationException("No cost variant configuration provided.");
        
        // parse global configuration XML
        Document gcDoc = builder.build(globalConfigurationFilename);
        Element gcRootElement = gcDoc.getRootElement();
        parseGlobalConfiguration(gcRootElement);

        //parse cost variant XML
        Document costVariantDoc = builder.build(costVariantConfigFile);
        Element costVariantRootElement = costVariantDoc.getRootElement();
        parseCostVariantConfiguration(costVariantRootElement);

        CommonProcessElementsParser cpeParser = new CommonProcessElementsParser(this);
        // parse each process model XML (.bpmn)
        for (String filename : processModelFilenames) {
            Document pmDoc = builder.build(filename);
            parseProcessCommonsAndModel(cpeParser, pmDoc.getRootElement(), filename);
        }

        SimulationConfigurationParser simParser = new SimulationConfigurationParser(this);
        // parse each simulation configuration XML
        for (String filename : simulationConfigurationFilenames) {
            Document scDoc = builder.build(filename);
            parseSimulationConfiguration(simParser, scDoc);
        }
    }
    
    protected void parseGlobalConfiguration(Element gcRootElement) throws ScyllaValidationException {
        GlobalConfigurationParser globalConfigurationParser = new GlobalConfigurationParser(this);
        globalConfiguration = globalConfigurationParser.parse(gcRootElement);
        
        String globalFileNameWithoutExtension = globalConfigurationFilename.substring(globalConfigurationFilename.lastIndexOf(Scylla.FILEDELIM)+1, globalConfigurationFilename.lastIndexOf(".xml"));
        globalConfiguration.setFileNameWithoutExtension(globalFileNameWithoutExtension);
        
        // plugins to parse global configuration
        GlobalConfigurationParserPluggable.runPlugins(this, globalConfiguration, gcRootElement);

        DateTimeUtils.setZoneId(globalConfiguration.getZoneId());
    }

    protected void parseCostVariantConfiguration(Element costVariantRootElement) throws ScyllaValidationException {
        CostVariantConfigurationParser costVariantConfigurationParser = new CostVariantConfigurationParser();
        costVariantConfiguration = costVariantConfigurationParser.parse(costVariantRootElement);

    }
    
    protected void parseProcessCommonsAndModel(CommonProcessElementsParser cpeParser, Element pmRootElement, String filename) throws ScyllaValidationException {
        // parse common process elements from XML (BPMN)
        CommonProcessElements commonProcessElementsFromFile = cpeParser.parse(pmRootElement);
        String fileNameWithoutExtension = filename.substring(filename.lastIndexOf(Scylla.FILEDELIM)+1, filename.lastIndexOf(".bpmn"));
        commonProcessElementsFromFile.setBpmnFileNameWithoutExtension(fileNameWithoutExtension);

        // plugins to parse common process elements
        CommonProcessElementsParserPluggable.runPlugins(this, commonProcessElementsFromFile, pmRootElement);

        // parse process model(s) from XML (BPMN)
        ProcessModelParser pmParser = new ProcessModelParser(this);
        pmParser.setCommonProcessElements(commonProcessElementsFromFile);
        ProcessModel processModelFromFile = pmParser.parse(pmRootElement);
        String processId = processModelFromFile.getId();
        if (processModels.containsKey(processId)) {
            throw new ScyllaValidationException("Duplicate process model with id " + processId + ".");
        }

        // plugins to parse process model(s)
        ProcessModelParserPluggable.runPlugins(this, processModelFromFile, pmRootElement);

        processModels.put(processId, processModelFromFile);
        commonProcessElements.put(processId, commonProcessElementsFromFile);
    }
    
    protected void parseSimulationConfiguration(SimulationConfigurationParser simParser, Document scDoc) throws ScyllaValidationException {
        SimulationConfiguration simulationConfigurationFromFile = simParser.parse(scDoc.getRootElement());
        String processId = simulationConfigurationFromFile.getProcessModel().getId();
        if (simulationConfigurations.containsKey(processId)) {
            throw new ScyllaValidationException(
                    "Multiple simulation configurations for process with id " + processId + ".");
        }

        // plugins to parse simulation configuration
        SimulationConfigurationParserPluggable.runPlugins(this, simulationConfigurationFromFile, scDoc);

        simulationConfigurations.put(processId, simulationConfigurationFromFile);
    }

    private void cleanup() {
		PluginLoader.flushDefault();
		//TODO remove the following:
		BatchPluginUtils.clear();
	}

	public GlobalConfiguration getGlobalConfiguration() {
        return globalConfiguration;
    }

    public Map<String, ProcessModel> getProcessModels() {
        return processModels;
    }
    
    public Map<String, SimulationConfiguration> getSimulationConfigurations() {
        return simulationConfigurations;
    }

    /**
     * Returns default output path if set
     * @return
     */
	public String getOutputPath() {
		return outputPath;
	}

	/**
	 * Methode to manually override default output path
	 * @param outputPath : A String path to a folder
	 */
	public void setOutputPath(String outputPath) {
		this.outputPath = normalizePath(outputPath);
	}
}
