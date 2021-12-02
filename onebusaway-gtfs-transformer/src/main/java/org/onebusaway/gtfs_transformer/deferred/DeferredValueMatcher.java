/**
 * Copyright (C) 2012 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.onebusaway.gtfs_transformer.deferred;

import org.apache.commons.beanutils.Converter;
import org.onebusaway.gtfs.model.AgencyAndId;
import org.onebusaway.gtfs.model.IdentityBean;
import org.onebusaway.gtfs.serialization.GtfsReader;
import org.onebusaway.gtfs_transformer.match.ValueMatcher;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DeferredValueMatcher implements ValueMatcher {

  private final DeferredValueSupport _support;

  private final Object _value;

  private Object _resolvedValue = null;

  private boolean _resolvedValueSet = false;

  public DeferredValueMatcher(GtfsReader reader, EntitySchemaCache schemaCache,
      Object value) {
    _support = new DeferredValueSupport(reader, schemaCache);
    _value = value;
  }

  public boolean matches(Class<?> parentEntityType, String propertyName,
      Object value) {
    if (value == null) {
      return _value == null;
    } else if (_value == null) {
      return false;
    }
    if (_resolvedValueSet) {
      return value.equals(_resolvedValue);
    }
    Class<?> expectedValueType = value.getClass();
    Class<?> actualValueType = _value.getClass();
    if (expectedValueType.isAssignableFrom(actualValueType)) {
      if (isRegexObj(_value)) {
        return regexMatch((String)value, ((String)_value));
      }
      return value.equals(_value);
    }
    if (actualValueType == String.class) {
      String actualValue = (String) _value;
      if (expectedValueType == AgencyAndId.class) {
        AgencyAndId expectedId = (AgencyAndId) value;
        if (isRegexObj(_value)) {
          return regexMatch(expectedId.getId(), actualValue);
        }
        return expectedId.getId().equals(actualValue);
      } else if (expectedValueType == Double.class && isRangeObj(_value)) {
          return rangeValue.contains((Double)value);
      } else if (IdentityBean.class.isAssignableFrom(expectedValueType)) {
        IdentityBean<?> bean = (IdentityBean<?>) value;
        Object expectedId = bean.getId();
        if (expectedId == null) {
          return false;
        }
        if (expectedId instanceof AgencyAndId) {
          AgencyAndId expectedFullId = (AgencyAndId) expectedId;
          return expectedFullId.getId().equals(actualValue);
        } else if (expectedId instanceof String) {
          return expectedId.equals(actualValue);
        }
      } else {
        Converter converter = _support.resolveConverter(parentEntityType,
            propertyName, expectedValueType);
        if (converter != null) {
          _resolvedValue = converter.convert(expectedValueType, _value);
          _resolvedValueSet = true;
          return value.equals(_resolvedValue);
        } else {
          throw new IllegalStateException(
              "no type conversion from type String to type \""
                  + expectedValueType.getName() + "\" for value comparison");
        }
      }
    }
    throw new IllegalStateException("no type conversion from type \""
        + actualValueType.getName() + "\" to type \""
        + expectedValueType.getName() + "\" for value comparison");
  }

  private Boolean isRegexObj = null;
  private boolean isRegexObj(Object value) {
    if (isRegexObj == null) {
      isRegexObj = (_value instanceof String && isRegex((String) _value));
    }
    return isRegexObj;
  }

  private Boolean isRangeObj = null;
  private Range rangeValue = null;
  private boolean isRangeObj(Object value) {
    if (isRangeObj == null) {
      if (_value instanceof String) {
        rangeValue = extractRangeIfApplicable((String) value);
        if (rangeValue != null) {
          isRangeObj = true;
        }
      }
    }
    return isRangeObj;
  }
  private Range extractRangeIfApplicable(String value) {
    Pattern pattern = Pattern.compile("r/(\\d+(\\.\\d+)?)/(\\d+(\\.\\d+)?)/");
    Matcher matcher = pattern.matcher(value);
    if (matcher.matches()) {
      return new Range(Double.parseDouble(matcher.group(1)),
          Double.parseDouble(matcher.group(3)));
    }
    return null;
  }

  private boolean isRegex(String pattern) {
    return pattern.startsWith("m/") && pattern.endsWith("/");
  }
  private String getRegexFromPattern(String pattern) {
    return pattern.substring(2, pattern.length()-1);
  }
  private boolean regexMatch(String value, String pattern) {
    String regexPattern = getRegexFromPattern(pattern);
    boolean rc = value.matches(regexPattern);
    return rc;
  }

  private static class Range{
    double min, max;
    public Range(double min, double max){
      this.min = min;
      this.max = max;
    }

    public boolean contains(double value){
      return min <= value && value <= max;
    }
  }
}
