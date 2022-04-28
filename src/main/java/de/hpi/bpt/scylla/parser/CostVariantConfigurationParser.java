package de.hpi.bpt.scylla.parser;

import de.hpi.bpt.scylla.exception.ScyllaValidationException;
import de.hpi.bpt.scylla.model.global.CostVariantConfiguration;
import org.jdom2.Element;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class CostVariantConfigurationParser implements IDOMParser<CostVariantConfiguration> {
    @Override
    public CostVariantConfiguration parse(Element rootElement) throws ScyllaValidationException {

        Map<String, Double> costVariantMap = new HashMap<>();

        List<Element> costVariantConfigurationElements = rootElement.getChildren();

        // get number of simulation runs
        Integer count = Integer.valueOf(rootElement.getAttributeValue("count"));

        costVariantConfigurationElements.forEach(element -> {

            if (!Objects.equals(element.getName(), "fixed_cost")) {
                // if not fixed cost, extract id and frequency
                String variantID = element.getAttributeValue("id");
                Double frequency = Double.valueOf(element.getAttributeValue("frequency"));
                costVariantMap.put(variantID, frequency);
            }
        });

        return new CostVariantConfiguration(costVariantMap, count);
    }
}
