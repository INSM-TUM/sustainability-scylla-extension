package de.hpi.bpt.scylla.model.global;

import java.util.*;

public class CostVariantConfiguration {
    private final List<String> costVariants;

    public CostVariantConfiguration(Map<String, Double> costVariantProbabilities, Integer simulationRuns) {
        costVariants = new ArrayList<>();

        // for each cost variant and its probabilities, as well as the number of simulation runs,
        // randomly determine order of cost variants
        costVariantProbabilities.forEach((variant, frequency) -> {

            double count = frequency * simulationRuns;
            int variantCount = (int) Math.round(count);
            for (int i = 0; i < variantCount; i++) {
                costVariants.add(variant);
            }
        });
        Collections.shuffle(costVariants);
        System.out.println(costVariants);
        System.out.println(costVariants.size());

    }

    public String takeCostVariant(int instanceID) {

        if (costVariants.size() == 0) {
            System.out.println("tried taking cost variant but was empty");
        }

        if (costVariants.size() < instanceID -1) {
            System.out.println("tried taking cost variant but there werent enough");
        }

        return costVariants.get(instanceID -1);
    }
}
