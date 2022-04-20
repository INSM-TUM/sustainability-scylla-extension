package de.hpi.bpt.scylla.parser;

import de.hpi.bpt.scylla.exception.ScyllaValidationException;
import de.hpi.bpt.scylla.model.global.CostVariantConfiguration;
import org.jdom2.Element;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CostVariantConfigurationParser implements IDOMParser<CostVariantConfiguration> {
    @Override
    public CostVariantConfiguration parse(Element rootElement) throws ScyllaValidationException {

        Map<String, Double> costVariantMap = new HashMap<>();

        List<Element> globalConfigurationElements = rootElement.getChildren();

        Integer count = Integer.valueOf(rootElement.getAttributeValue("count"));

        globalConfigurationElements.forEach(element -> {
            String variantID = element.getAttributeValue("id");
            Double frequency = Double.valueOf(element.getAttributeValue("frequency"));
            costVariantMap.put(variantID, frequency);

            System.out.println(element.getAttributeValue("id"));
            System.out.println(element.getAttributeValue("frequency"));});

        return new CostVariantConfiguration(costVariantMap, count);
    }
}
