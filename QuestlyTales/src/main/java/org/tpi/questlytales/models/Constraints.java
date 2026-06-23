package org.tpi.questlytales.models;

import java.util.List;

public class Constraints {
    private Integer minValue;
    private Integer maxValue;
    private Integer minLength;
    private Integer maxLength;
    private List<String> authorizedValues;
    
    public Constraints() {}
    
    public Constraints setMinValue(Integer minValue) {
        this.minValue = minValue;
        return this;
    }

    public Constraints setMinLength(Integer minLength) {
        this.minLength = minLength;
        return this;
    }
    
    public Constraints setMaxValue(Integer maxValue) {
        this.maxValue = maxValue;
        return this;
    }

    public Constraints setMaxLength(Integer maxLength) {
        this.maxLength = maxLength;
        return this;
    }

    public Constraints setAuthorizedValues(List<String> authorizedValues) {
        this.authorizedValues = authorizedValues;
        return this;
    }
    
    public Integer getMinValue() {
        return minValue;
    }
    
    public Integer getMaxValue() {
        return maxValue;
    }

    public Integer getMinLength() {
        return minLength;
    }

    public Integer getMaxLength() {
        return maxLength;
    }
    
    public List<String> getAuthorizedValues() {
        return authorizedValues;
    }
}