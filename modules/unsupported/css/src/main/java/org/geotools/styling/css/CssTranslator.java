/*
 *    GeoTools - The Open Source Java GIS Toolkit
 *    http://geotools.org
 *
 *    (C) 2014, Open Source Geospatial Foundation (OSGeo)
 *
 *    This library is free software; you can redistribute it and/or
 *    modify it under the terms of the GNU Lesser General Public
 *    License as published by the Free Software Foundation;
 *    version 2.1 of the License.
 *
 *    This library is distributed in the hope that it will be useful,
 *    but WITHOUT ANY WARRANTY; without even the implied warranty of
 *    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *    Lesser General Public License for more details.
 */
package org.geotools.styling.css;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.transform.TransformerException;

import org.apache.commons.io.FileUtils;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.feature.NameImpl;
import org.geotools.styling.ColorMap;
import org.geotools.styling.NamedLayer;
import org.geotools.styling.Rule;
import org.geotools.styling.SLDTransformer;
import org.geotools.styling.StyleFactory;
import org.geotools.styling.StyledLayerDescriptor;
import org.geotools.styling.builder.ChannelSelectionBuilder;
import org.geotools.styling.builder.ColorMapBuilder;
import org.geotools.styling.builder.ColorMapEntryBuilder;
import org.geotools.styling.builder.ContrastEnhancementBuilder;
import org.geotools.styling.builder.FeatureTypeStyleBuilder;
import org.geotools.styling.builder.FillBuilder;
import org.geotools.styling.builder.FontBuilder;
import org.geotools.styling.builder.GraphicBuilder;
import org.geotools.styling.builder.HaloBuilder;
import org.geotools.styling.builder.LineSymbolizerBuilder;
import org.geotools.styling.builder.MarkBuilder;
import org.geotools.styling.builder.PointPlacementBuilder;
import org.geotools.styling.builder.PointSymbolizerBuilder;
import org.geotools.styling.builder.PolygonSymbolizerBuilder;
import org.geotools.styling.builder.RasterSymbolizerBuilder;
import org.geotools.styling.builder.RuleBuilder;
import org.geotools.styling.builder.StrokeBuilder;
import org.geotools.styling.builder.StyleBuilder;
import org.geotools.styling.builder.SymbolizerBuilder;
import org.geotools.styling.builder.TextSymbolizerBuilder;
import org.geotools.styling.css.Value.Function;
import org.geotools.styling.css.Value.Literal;
import org.geotools.styling.css.Value.MultiValue;
import org.geotools.styling.css.selector.AbstractSelectorVisitor;
import org.geotools.styling.css.selector.Data;
import org.geotools.styling.css.selector.Or;
import org.geotools.styling.css.selector.PseudoClass;
import org.geotools.styling.css.selector.Selector;
import org.geotools.styling.css.selector.TypeName;
import org.geotools.styling.css.util.FeatureTypeGuesser;
import org.geotools.styling.css.util.OgcFilterBuilder;
import org.geotools.styling.css.util.ScaleRangeExtractor;
import org.geotools.styling.css.util.TypeNameExtractor;
import org.geotools.util.Range;
import org.opengis.feature.type.FeatureType;
import org.opengis.feature.type.Name;
import org.opengis.filter.Filter;
import org.opengis.filter.FilterFactory2;
import org.opengis.filter.expression.Expression;
import org.opengis.style.Style;
import org.parboiled.Parboiled;
import org.parboiled.errors.ErrorUtils;
import org.parboiled.parserunners.ReportingParseRunner;
import org.parboiled.support.ParsingResult;
import org.w3c.dom.css.CSSRule;

/**
 * Transforms a GeoCSS into an equivalent GeoTools {@link Style} object
 * 
 * @author Andrea Aime - GeoSolutions
 */
public class CssTranslator {

    static final int MAX_OUTPUT_RULES = Integer.valueOf(System.getProperty(
            "org.geotools.css.maxOutputRules", "10000"));

    static final FilterFactory2 FF = CommonFactoryFinder.getFilterFactory2();

    /**
     * Matches the title tag inside a rule comment
     */
    static final Pattern TITLE_PATTERN = Pattern.compile("^.*@title\\s*(?:\\:\\s*)?(.+)\\s*$");

    /**
     * Matches the abstract tag inside a rule comment
     */
    static final Pattern ABSTRACT_PATTERN = Pattern
            .compile("^.*@abstract\\s*(?:\\:\\s*)?(.+)\\s*$");

    @SuppressWarnings("serial")
    static final Map<String, String> POLYGON_VENDOR_OPTIONS = new HashMap<String, String>() {
        {
            put("-gt-graphic-margin", "graphic-margin");
            put("-gt-fill-label-obstacle", "labelObstacle");
            put("-gt-fill-random", "random");
            put("-gt-fill-random-seed", "random-seed");
            put("-gt-fill-random-tile-size", "random-tile-size");
            put("-gt-fill-random-symbol-count", "random-symbol-count");
            put("-gt-fill-random-space-around", "random-space-around");
            put("-gt-fill-random-rotation", "random-rotation");

        }
    };

    @SuppressWarnings("serial")
    static final Map<String, String> TEXT_VENDOR_OPTIONS = new HashMap<String, String>() {
        {
            put("-gt-label-padding", "spaceAround");
            put("-gt-label-group", "group");
            put("-gt-label-max-displacement", "maxDisplacement");
            put("-gt-label-min-group-distance", "minGroupDistance");
            put("-gt-label-repeat", "repeat");
            put("-gt-label-all-group", "allGroup");
            put("-gt-label-remove-overlaps", "removeOverlaps");
            put("-gt-label-allow-overruns", "allowOverrun");
            put("-gt-label-follow-line", "followLine");
            put("-gt-label-max-angle-delta", "maxAngleDelta");
            put("-gt-label-auto-wrap", "autoWrap");
            put("-gt-label-force-ltr", "forceLeftToRight");
            put("-gt-label-conflict-resolution", "conflictResolution");
            put("-gt-label-fit-goodness", "goodnessOfFit");
            put("-gt-shield-resize", "graphic-resize");
            put("-gt-shield-margin", "graphic-margin");
        }
    };

    @SuppressWarnings("serial")
    static final Map<String, String> LINE_VENDOR_OPTIONS = new HashMap<String, String>() {
        {
            put("-gt-stroke-label-obstacle", "labelObstacle");
        }
    };

    @SuppressWarnings("serial")
    static final Map<String, String> POINT_VENDOR_OPTIONS = new HashMap<String, String>() {
        {
            put("-gt-mark-label-obstacle", "labelObstacle");
        }
    };

    /**
     * Translates a CSS stylesheet into an equivalent GeoTools {@link Style} object
     * 
     * @param stylesheet
     * @return
     */
    public Style translate(Stylesheet stylesheet) {
        return translate(stylesheet, MAX_OUTPUT_RULES);
    }

    /**
     * Translates a CSS stylesheet into an equivalent GeoTools {@link Style} object
     * 
     * @param stylesheet
     * @return
     */
    public Style translate(Stylesheet stylesheet, int maxCombinations) {
        List<CssRule> allRules = stylesheet.getRules();

        // prepare the full SLD builder
        StyleBuilder styleBuilder = new StyleBuilder();
        styleBuilder.name("Default Styler");

        // split rules by index and typename, then build the power set for each group and
        // generate the rules and symbolizers
        List<List<CssRule>> zIndexRules = organizeByZIndex(allRules);
        for (List<CssRule> rules : zIndexRules) {
            Collections.sort(rules, Collections.reverseOrder(new CssRuleComparator()));
            Map<String, List<CssRule>> typenameRules = organizeByTypeName(rules);

            // build the SLD
            for (Map.Entry<String, List<CssRule>> entry : typenameRules.entrySet()) {
                // create the feature type style for this typename
                FeatureTypeStyleBuilder ftsBuilder = styleBuilder.featureTypeStyle();
                String featureTypeName = entry.getKey();
                List<CssRule> localRules = entry.getValue();
                if (featureTypeName != null) {
                    ftsBuilder.setFeatureTypeNames(Arrays.asList((Name) new NameImpl(
                            featureTypeName)));
                }
                final FeatureType targetFeatureType = getTargetFeatureType(featureTypeName,
                        localRules);
                if (targetFeatureType != null) {
                    // attach the target feature type to all Data selectors to allow range based
                    // simplification
                    for (CssRule rule : localRules) {
                        rule.getSelector().accept(new AbstractSelectorVisitor() {
                            @Override
                            public Object visit(Data data) {
                                data.featureType = targetFeatureType;
                                return super.visit(data);
                            }
                        });
                    }
                }

                // at this point we can have rules with selectors having two scale ranges
                // in or, we should split them, as we cannot represent them in SLD
                // (and yes, this changes their selectivity a bit, could not find a reasonable
                // solution out of this so far, past the power set we might end up with
                // and and of two selectors, that internally have ORs of scales, which could
                // be quite complicated to un-tangle)
                List<CssRule> flattenedRules = flattenScaleRanges(localRules);

                // expand the css rules power set
                RulePowerSetBuilder builder = new RulePowerSetBuilder(flattenedRules,
                        maxCombinations);
                List<CssRule> combinedRules = builder.buildPowerSet();

                Collections.sort(combinedRules, Collections.reverseOrder(new CssRuleComparator()));

                // create a SLD rule for each css one, making them exclusive, that is,
                // remove from each rule the union of the zoom/data domain matched by previous rules
                DomainCoverage coverage = new DomainCoverage(targetFeatureType);
                for (int i = 0; i < combinedRules.size(); i++) {
                    // skip eventual combinations that are not sporting any
                    // root pseudo class
                    CssRule cssRule = combinedRules.get(i);
                    if (!cssRule.hasSymbolizerProperty()) {
                        continue;
                    }
                    List<CssRule> derivedRules = coverage.addRule(cssRule);
                    for (CssRule derived : derivedRules) {
                        buildSldRule(derived, ftsBuilder, targetFeatureType);
                    }
                }
            }
        }

        return styleBuilder.build();
    }

    /**
     * SLD rules can have two or more selectors in OR using different scale ranges, however the SLD
     * model does not allow for that. Flatten them into N different rules, with the same properties,
     * but different selectors
     * 
     * @param rules
     * @return
     */
    private List<CssRule> flattenScaleRanges(List<CssRule> rules) {
        List<CssRule> result = new ArrayList<>();
        for (CssRule rule : rules) {
            if (rule.getSelector() instanceof Or) {
                Or or = (Or) rule.getSelector();
                List<Selector> others = new ArrayList<>();
                for (Selector child : or.children) {
                    ScaleRangeExtractor extractor = new ScaleRangeExtractor();
                    Range<Double> range = extractor.getScaleRange(child);
                    if (range == null) {
                        others.add(child);
                    } else {
                        result.add(new CssRule(child, rule.getProperties(), rule.getComment()));
                    }
                }
                if (others.size() == 1) {
                    result.add(new CssRule(others.get(0), rule.getProperties(), rule.getComment()));
                } else if (others.size() > 0) {
                    result.add(new CssRule(new Or(others), rule.getProperties(), rule.getComment()));
                }
            } else {
                result.add(rule);
            }
        }

        return result;
    }

    /**
     * This method builds a target feature type based on the provided rules, subclasses can override
     * and maybe pick the feature type from a well known source
     */
    protected FeatureType getTargetFeatureType(String featureTypeName, List<CssRule> rules) {
        FeatureTypeGuesser guesser = new FeatureTypeGuesser();
        for (CssRule rule : rules) {
            guesser.addRule(rule);
        }

        return guesser.getFeatureType();
    }

    /**
     * Splits the rules into different sets by feature type name
     * 
     * @param rules
     * @return
     */
    private Map<String, List<CssRule>> organizeByTypeName(List<CssRule> rules) {
        TypeNameExtractor extractor = new TypeNameExtractor();
        for (CssRule rule : rules) {
            rule.getSelector().accept(extractor);
        }

        // extract all typename specific rules
        Map<String, List<CssRule>> result = new LinkedHashMap<>();
        Set<TypeName> typeNames = extractor.getTypeNames();
        if (typeNames.size() == 1 && typeNames.contains(TypeName.DEFAULT)) {
            // no layer specific stuff
            result.put(TypeName.DEFAULT.name, rules);
        } else {
            // remove the default from the type names, otherwise we
            // are going to generate rules/symbolizers that are contained both in the
            // layer specific feature type styles, and in a generic one at the
            // end, which will make them draw multiple times and affecting z order perception
            typeNames.remove(TypeName.DEFAULT);
        }

        for (TypeName tn : typeNames) {
            List<CssRule> typeNameRules = new ArrayList<>();
            for (CssRule rule : rules) {
                Selector combined = Selector.and(tn, rule.getSelector());
                if (combined != Selector.REJECT) {
                    typeNameRules
                            .add(new CssRule(combined, rule.getProperties(), rule.getComment()));
                }
            }
            result.put(tn.name, typeNameRules);
        }

        return result;
    }

    /**
     * Organizes them rules by ascending z-index
     * 
     * @param rules
     * @return
     */
    private List<List<CssRule>> organizeByZIndex(List<CssRule> rules) {
        // collect and sort all the indexes first
        TreeSet<Integer> indexes = new TreeSet<>(new ZIndexComparator());
        for (CssRule rule : rules) {
            Set<Integer> ruleIndexes = rule.getZIndexes();
            indexes.addAll(ruleIndexes);
        }

        // now for each level extract the sub-rules attached to that level,
        // considering that properties not associated to a level, bind to all levels
        List<List<CssRule>> result = new ArrayList<>();
        int symbolizerPropertyCount = 0;
        for (Integer index : indexes) {
            List<CssRule> rulesByIndex = new ArrayList<>();
            for (CssRule rule : rules) {
                CssRule subRule = rule.getSubRuleByZIndex(index);
                if (subRule != null) {
                    if (subRule.hasSymbolizerProperty()) {
                        symbolizerPropertyCount++;
                    }
                    rulesByIndex.add(subRule);
                }
            }
            // do we have at least one property that will trigger the generation
            // of a symbolizer in here?
            if (symbolizerPropertyCount > 0) {
                result.add(rulesByIndex);
            }
        }

        return result;
    }

    /**
     * Turns an SLD compatible {@link CSSRule} into a {@link Rule}, appending it to the
     * {@link FeatureTypeStyleBuilder}
     * 
     * @param cssRule
     * @param fts
     * @param targetFeatureType
     */
    void buildSldRule(CssRule cssRule, FeatureTypeStyleBuilder fts, FeatureType targetFeatureType) {
        // check we have a valid scale range
        Range<Double> scaleRange = ScaleRangeExtractor.getScaleRange(cssRule);
        if (scaleRange != null && scaleRange.isEmpty()) {
            return;
        }

        // check we have a valid filter
        Filter filter = OgcFilterBuilder.buildFilter(cssRule.getSelector(), targetFeatureType);
        if (filter == Filter.EXCLUDE) {
            return;
        }

        // ok, build the rule
        RuleBuilder ruleBuilder;
        ruleBuilder = fts.rule();
        ruleBuilder.filter(filter);
        String title = getCombinedTag(cssRule.getComment(), TITLE_PATTERN, ", ");
        if (title != null) {
            ruleBuilder.title(title);
        }
        String ruleAbstract = getCombinedTag(cssRule.getComment(), ABSTRACT_PATTERN, "\n");
        if (ruleAbstract != null) {
            ruleBuilder.ruleAbstract(ruleAbstract);
        }
        if (scaleRange != null) {
            Double minValue = scaleRange.getMinValue();
            if (minValue != null && minValue > 0) {
                ruleBuilder.min(minValue);
            }
            Double maxValue = scaleRange.getMaxValue();
            if (maxValue != null && maxValue < Double.POSITIVE_INFINITY) {
                ruleBuilder.max(maxValue);
            }
        }

        // see if we can fold the stroke into a polygon symbolizer
        boolean generateStroke = cssRule.hasProperty(PseudoClass.ROOT, "stroke");
        boolean lineSymbolizerSpecificProperties = cssRule.hasAnyProperty(PseudoClass.ROOT,
                LINE_VENDOR_OPTIONS.keySet());
        boolean includeStrokeInPolygonSymbolizer = generateStroke
                && !lineSymbolizerSpecificProperties;
        boolean generatePolygonSymbolizer = cssRule.hasProperty(PseudoClass.ROOT, "fill");
        if (generatePolygonSymbolizer) {
            addPolygonSymbolizer(cssRule, ruleBuilder, includeStrokeInPolygonSymbolizer);
        }
        if (generateStroke && !(generatePolygonSymbolizer && includeStrokeInPolygonSymbolizer)) {
            addLineSymbolizer(cssRule, ruleBuilder);
        }
        if (cssRule.hasProperty(PseudoClass.ROOT, "mark")) {
            addPointSymbolizer(cssRule, ruleBuilder);
        }
        if (cssRule.hasProperty(PseudoClass.ROOT, "label")) {
            addTextSymbolizer(cssRule, ruleBuilder);
        }
        if (cssRule.hasProperty(PseudoClass.ROOT, "raster-channels")) {
            rasterSymbolizer(cssRule, ruleBuilder);
        }
    }

    private String getCombinedTag(String comment, Pattern p, String separator) {
        if (comment == null || comment.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();

        for (String line : comment.split("\n")) {
            Matcher matcher = p.matcher(line);
            if (matcher.matches()) {
                String text = matcher.group(1).trim();
                if (!text.isEmpty()) {
                    if (sb.length() > 0) {
                        sb.append(separator);
                    }
                    sb.append(text);
                }
            }
        }

        if (sb.length() > 0) {
            return sb.toString();
        } else {
            return null;
        }
    }

    /**
     * Builds a polygon symbolizer into the current rule, if a <code>fill</code> property is found
     * 
     * @param cssRule
     * @param ruleBuilder
     * @param includeStrokeInPolygonSymbolizer
     */
    private void addPolygonSymbolizer(CssRule cssRule, RuleBuilder ruleBuilder,
            boolean includeStrokeInPolygonSymbolizer) {
        Map<String, List<Value>> values;
        if (includeStrokeInPolygonSymbolizer) {
            values = cssRule.getPropertyValues(PseudoClass.ROOT, "fill", "-gt-graphic-margin",
                    "stroke");
        } else {
            values = cssRule.getPropertyValues(PseudoClass.ROOT, "fill", "-gt-graphic-margin");
        }
        if (values == null || values.isEmpty()) {
            return;
        }
        int repeatCount = getMaxRepeatCount(values);
        for (int i = 0; i < repeatCount; i++) {
            PolygonSymbolizerBuilder pb = ruleBuilder.polygon();
            Expression fillGeometry = getExpression(values, "fill-geometry", i);
            if (fillGeometry != null) {
                pb.geometry(fillGeometry);
            }
            FillBuilder fb = pb.fill();
            buildFill(cssRule, fb, values, i);
            if (includeStrokeInPolygonSymbolizer) {
                StrokeBuilder sb = pb.stroke();
                buildStroke(cssRule, sb, values, i);
            }
            addVendorOptions(pb, POLYGON_VENDOR_OPTIONS, values, i);
        }
    }

    /**
     * Builds a point symbolizer into the current rule, if a <code>mark</code> property is found
     * 
     * @param cssRule
     * @param ruleBuilder
     */
    private void addPointSymbolizer(CssRule cssRule, RuleBuilder ruleBuilder) {
        Map<String, List<Value>> values = cssRule.getPropertyValues(PseudoClass.ROOT, "mark");
        if (values == null || values.isEmpty()) {
            return;
        }
        int repeatCount = getMaxRepeatCount(values);
        for (int i = 0; i < repeatCount; i++) {
            final PointSymbolizerBuilder pb = ruleBuilder.point();
            Expression markGeometry = getExpression(values, "mark-geometry", i);
            if (markGeometry != null) {
                pb.geometry(markGeometry);
            }
            for (Value markValue : getMultiValue(values, "mark", i)) {
                new SubgraphicBuilder("mark", markValue, values, cssRule, i) {

                    @Override
                    protected GraphicBuilder getGraphicBuilder() {
                        return pb.graphic();
                    }

                };
            }

            addVendorOptions(pb, POINT_VENDOR_OPTIONS, values, i);
        }
    }

    /**
     * Builds a text symbolizer into the current rule, if a <code>label</code> property is found
     * 
     * @param cssRule
     * @param ruleBuilder
     */
    private void addTextSymbolizer(CssRule cssRule, RuleBuilder ruleBuilder) {
        Map<String, List<Value>> values = cssRule.getPropertyValues(PseudoClass.ROOT, "label",
                "font", "shield", "halo");
        if (values == null || values.isEmpty()) {
            return;
        }
        int repeatCount = getMaxRepeatCount(values);
        for (int i = 0; i < repeatCount; i++) {
            final TextSymbolizerBuilder tb = ruleBuilder.text();
            Expression labelGeometry = getExpression(values, "label-geometry", i);
            if (labelGeometry != null) {
                tb.geometry(labelGeometry);
            }

            // special handling for label, we allow multi-valued and treat as concatenation
            Value labelValue = getValue(values, "label", i);
            Expression labelExpression;
            if (labelValue instanceof MultiValue) {
                MultiValue m = (MultiValue) labelValue;
                List<Expression> parts = new ArrayList<>();
                for (Value mv : m.values) {
                    parts.add(mv.toExpression());
                }

                labelExpression = FF.function("Concatenate",
                        parts.toArray(new Expression[parts.size()]));
            } else {
                labelExpression = labelValue.toExpression();
            }
            tb.label(labelExpression);

            double[] anchor = getDoubleArray(values, "label-anchor", i);
            double[] offsets = getDoubleArray(values, "label-offset", i);
            if (offsets != null && offsets.length == 1) {
                tb.linePlacement().offset((float) offsets[0]);
            } else if (offsets != null || anchor != null) {
                PointPlacementBuilder ppb = tb.pointPlacement();
                if (anchor != null) {
                    if (anchor.length == 2) {
                        ppb.anchor().x(anchor[0]);
                        ppb.anchor().y(anchor[1]);
                    } else {
                        throw new IllegalArgumentException(
                                "Invalid anchor specification, should be two "
                                        + "floats between 0 and 1 with a space in between, instead it is "
                                        + getValue(values, "label-anchor", i));
                    }
                }
                if (offsets != null) {
                    if (offsets.length == 2) {
                        ppb.displacement().x(offsets[0]);
                        ppb.displacement().y(offsets[1]);
                    } else {
                        throw new IllegalArgumentException(
                                "Invalid anchor specification, should be two "
                                        + "floats (or 1 for line placement with a certain offset) instead it is "
                                        + getValue(values, "label-anchor", i));
                    }
                }
            }
            Expression rotation = getMeasureExpression(values, "label-rotation", i, "deg");
            if (rotation != null) {
                tb.pointPlacement().rotation(rotation);
            }
            for (Value shieldValue : getMultiValue(values, "shield", i)) {
                new SubgraphicBuilder("shield", shieldValue, values, cssRule, i) {

                    @Override
                    protected GraphicBuilder getGraphicBuilder() {
                        return tb.shield();
                    }

                };
            }
            // the color
            Expression fill = getExpression(values, "font-fill", i);
            if (fill != null) {
                tb.fill().color(fill);
            }
            Expression opacity = getExpression(values, "font-opacity", i);
            if (opacity != null) {
                tb.fill().opacity(opacity);
            }
            // the fontdi
            Map<String, List<Value>> fontLikeProperties = cssRule.getPropertyValues(
                    PseudoClass.ROOT, "font");
            if (!fontLikeProperties.isEmpty()
                    && (fontLikeProperties.size() > 1 || fontLikeProperties.get("font-fill") == null)) {
                FontBuilder fb = tb.newFont();
                Expression fontFamily = getExpression(values, "font-family", i);
                if (fontFamily != null) {
                    fb.family(fontFamily);
                }
                Expression fontStyle = getExpression(values, "font-style", i);
                if (fontStyle != null) {
                    fb.style(fontStyle);
                }
                Expression fontWeight = getExpression(values, "font-weight", i);
                if (fontWeight != null) {
                    fb.weight(fontWeight);
                }
                Expression fontSize = getMeasureExpression(values, "font-size", i, "px");
                if (fontSize != null) {
                    fb.size(fontSize);
                }
            }
            // the halo
            if (!cssRule.getPropertyValues(PseudoClass.ROOT, "halo").isEmpty()) {
                HaloBuilder hb = tb.halo();
                Expression haloRadius = getMeasureExpression(values, "halo-radius", i, "px");
                if (haloRadius != null) {
                    hb.radius(haloRadius);
                }
                Expression haloColor = getExpression(values, "halo-color", i);
                if (haloColor != null) {
                    hb.fill().color(haloColor);
                }
                Expression haloOpacity = getExpression(values, "halo-opacity", i);
                if (haloOpacity != null) {
                    hb.fill().opacity(haloOpacity);
                }
            }
            Expression priority = getExpression(values, "-gt-label-priority", i);
            if (priority != null) {
                tb.priority(priority);
            }
            addVendorOptions(tb, TEXT_VENDOR_OPTIONS, values, i);
        }
    }

    /**
     * Builds a raster symbolizer into the current rule, if a <code>raster-channels</code> property
     * is found
     * 
     * @param cssRule
     * @param ruleBuilder
     */
    private void rasterSymbolizer(CssRule cssRule, RuleBuilder ruleBuilder) {
        Map<String, List<Value>> values = cssRule.getPropertyValues(PseudoClass.ROOT, "raster");
        if (values == null || values.isEmpty()) {
            return;
        }

        int repeatCount = getMaxRepeatCount(values);
        for (int i = 0; i < repeatCount; i++) {
            RasterSymbolizerBuilder rb = ruleBuilder.raster();
            String[] channelNames = getStringArray(values, "raster-channels", i);
            String[] constrastEnhancements = getStringArray(values, "raster-contrast-enhancement",
                    i);
            double[] gammas = getDoubleArray(values, "raster-gamma", i);
            if (!"auto".equals(channelNames[0])) {
                ChannelSelectionBuilder cs = rb.channelSelection();
                if (channelNames.length == 1) {
                    applyContrastEnhancement(cs.gray().channelName(channelNames[0])
                            .contrastEnhancement(), constrastEnhancements, gammas, 0);
                } else if (channelNames.length == 2 || channelNames.length > 3) {
                    throw new IllegalArgumentException(
                            "raster-channels can accept the name of one or three bands, not "
                                    + channelNames.length);
                } else {
                    applyContrastEnhancement(cs.red().channelName(channelNames[0])
                            .contrastEnhancement(), constrastEnhancements, gammas, 0);
                    applyContrastEnhancement(cs.green().channelName(channelNames[1])
                            .contrastEnhancement(), constrastEnhancements, gammas, 1);
                    applyContrastEnhancement(cs.blue().channelName(channelNames[2])
                            .contrastEnhancement(), constrastEnhancements, gammas, 2);
                }
            } else {
                applyContrastEnhancement(rb.contrastEnhancement(), constrastEnhancements, gammas, 0);
            }

            Expression opacity = getExpression(values, "raster-opacity", i);
            if (opacity != null) {
                rb.opacity(opacity);
            }
            Expression geom = getExpression(values, "raster-geometry", i);
            if (geom != null) {
                rb.geometry(geom);
            }
            Value v = getValue(values, "raster-color-map", i);
            if (v != null) {
                if (v instanceof Function) {
                    v = new MultiValue(v);
                }
                if (!(v instanceof MultiValue)) {
                    throw new IllegalArgumentException(
                            "Invalid color map, it must be comprised of one or more color-map-entry function: "
                                    + v);
                } else {
                    MultiValue cm = (MultiValue) v;
                    ColorMapBuilder cmb = rb.colorMap();
                    for (Value entry : cm.values) {
                        if (!(entry instanceof Function)) {
                            throw new IllegalArgumentException(
                                    "Invalid color map content, it must be a color-map-entry function"
                                            + entry);
                        }
                        Function f = (Function) entry;
                        if (!"color-map-entry".equals(f.name)) {
                            throw new IllegalArgumentException(
                                    "Invalid color map content, it must be a color-map-entry function"
                                            + entry);
                        } else if (f.parameters.size() < 2 || f.parameters.size() > 3) {
                            throw new IllegalArgumentException(
                                    "Invalid color map content, it must be a color-map-entry function "
                                            + "with either 2 parameters (color and value) or 3 parameters "
                                            + "(color, value and opacity)" + entry);
                        }
                        ColorMapEntryBuilder eb = cmb.entry();
                        eb.color(f.parameters.get(0).toExpression());
                        eb.quantity(f.parameters.get(1).toExpression());
                        if (f.parameters.size() == 3) {
                            eb.opacity(f.parameters.get(2).toExpression());
                        }
                    }
                    String type = getLiteral(values, "raster-color-map-type", i, null);
                    if (type != null) {
                        if ("intervals".equals(type)) {
                            cmb.type(ColorMap.TYPE_INTERVALS);
                        } else if ("ramp".equals(type)) {
                            cmb.type(ColorMap.TYPE_RAMP);
                        } else if ("values".equals(type)) {
                            cmb.type(ColorMap.TYPE_VALUES);
                        } else {
                            throw new IllegalArgumentException("Invalid color map type " + type);
                        }
                    }
                }
            }
        }
    }

    /**
     * Applies contrast enhcancement for the i-th band
     * 
     * @param ceb
     * @param constrastEnhancements
     * @param gammas
     * @param i
     */
    private void applyContrastEnhancement(ContrastEnhancementBuilder ceb,
            String[] constrastEnhancements, double[] gammas, int i) {
        if (constrastEnhancements != null && constrastEnhancements.length > 0) {
            String contrastEnhancementName;
            if (constrastEnhancements.length > i) {
                contrastEnhancementName = constrastEnhancements[0];
            } else {
                contrastEnhancementName = constrastEnhancements[i];
            }
            if ("histogram".equals(contrastEnhancementName)) {
                ceb.histogram();
            } else if ("normalize".equals(contrastEnhancementName)) {
                ceb.normalize();
            } else if (!"none".equals(contrastEnhancementName)) {
                throw new IllegalArgumentException("Invalid contrast enhancement name "
                        + contrastEnhancementName
                        + ", valid values are 'none', 'histogram', 'normalize'");
            }
        } else {
            ceb.unset();
        }
        if (gammas != null && gammas.length > 0) {
            double gamma;
            if (gammas.length > i) {
                gamma = gammas[0];
            } else {
                gamma = gammas[i];
            }
            ceb.gamma(gamma);
        }
    }

    /**
     * Builds a graphic object into the current style build parent
     */
    abstract class SubgraphicBuilder {
        public SubgraphicBuilder(String propertyName, Value v, Map<String, List<Value>> values,
                CssRule cssRule, int i) {
            if (v != null) {
                if (!(v instanceof Function)) {
                    throw new IllegalArgumentException("The value of '" + propertyName
                            + "' must be a symbol or a url");
                }
                Function f = (Function) v;
                GraphicBuilder gb = getGraphicBuilder();
                if (Function.SYMBOL.equals(f.name)) {
                    buildMark(f.parameters.get(0), cssRule, propertyName, i, gb);
                } else if (Function.URL.equals(f.name)) {
                    Value graphicLocation = f.parameters.get(0);
                    String location = graphicLocation.toLiteral();
                    // to turn stuff into SLD we need to make sure the URL is a valid one
                    // try {
                    // new URL(location);
                    // } catch (MalformedURLException e) {
                    // location = "file://" + location;
                    // }
                    String mime = getLiteral(values, propertyName + "-mime", i, "image/jpeg");
                    gb.externalGraphic(location, mime);
                } else {
                    throw new IllegalArgumentException(
                            "'"
                                    + propertyName
                                    + "' accepts either a 'symbol' or a 'url' function, the following function is unrecognized: "
                                    + f);
                }

                Expression rotation = getMeasureExpression(values, propertyName + "-rotation", i,
                        "deg");
                if (rotation != null) {
                    gb.rotation(rotation);
                }
                Expression size = getMeasureExpression(values, propertyName + "-size", i, "px");
                if (size != null) {
                    gb.size(size);
                }
                if ("mark".equals(propertyName)) {
                    Expression opacity = getExpression(values, "mark-opacity", i);
                    if (opacity != null) {
                        gb.opacity(opacity);
                    }
                }
            }
        }

        protected abstract GraphicBuilder getGraphicBuilder();

    }

    /**
     * Builds the fill using a FillBuilder
     * 
     * @param cssRule
     * @param fb
     * @param values
     * @param i
     */
    private void buildFill(CssRule cssRule, final FillBuilder fb, Map<String, List<Value>> values,
            int i) {
        for (Value fillValue : getMultiValue(values, "fill", i)) {
            if (fillValue instanceof Function) {
                new SubgraphicBuilder("fill", fillValue, values, cssRule, i) {

                    @Override
                    protected GraphicBuilder getGraphicBuilder() {
                        return fb.graphicFill();
                    }
                };
            } else if (fillValue != null) {
                fb.color(getExpression(fillValue));
            }
        }
        Expression opacity = getExpression(values, "fill-opacity", i);
        if (opacity != null) {
            fb.opacity(opacity);
        }
    }

    /**
     * Adds a line symbolizer, assuming the <code>stroke<code> property is found
     * 
     * @param cssRule
     * @param ruleBuilder
     */
    private void addLineSymbolizer(CssRule cssRule, RuleBuilder ruleBuilder) {
        Map<String, List<Value>> values = cssRule.getPropertyValues(PseudoClass.ROOT, "stroke");
        if (values == null || values.isEmpty()) {
            return;
        }
        int repeatCount = getMaxRepeatCount(values);
        for (int i = 0; i < repeatCount; i++) {
            if (getValue(values, "stroke", i) == null) {
                continue;
            }

            LineSymbolizerBuilder lb = ruleBuilder.line();
            Expression strokeGeometry = getExpression(values, "stroke-geometry", i);
            if (strokeGeometry != null) {
                lb.geometry(strokeGeometry);
            }

            StrokeBuilder strokeBuilder = lb.stroke();
            buildStroke(cssRule, strokeBuilder, values, i);
            addVendorOptions(lb, LINE_VENDOR_OPTIONS, values, i);
        }
    }

    /**
     * Builds a stroke using the stroke buidler for the i-th set of property values
     * 
     * @param cssRule
     * @param strokeBuilder
     * @param values
     * @param i
     */
    private void buildStroke(CssRule cssRule, final StrokeBuilder strokeBuilder,
            final Map<String, List<Value>> values, final int i) {

        for (Value strokeValue : getMultiValue(values, "stroke", i)) {
            if (strokeValue instanceof Function) {
                new SubgraphicBuilder("stroke", strokeValue, values, cssRule, i) {

                    @Override
                    protected GraphicBuilder getGraphicBuilder() {
                        String repeat = getLiteral(values, "stroke-repeat", i, "repeat");
                        if ("repeat".equals(repeat)) {
                            return strokeBuilder.graphicStroke();
                        } else {
                            return strokeBuilder.fillBuilder();
                        }
                    }
                };
            } else if (strokeValue != null) {
                strokeBuilder.color(strokeValue.toExpression());
            }
        }
        Expression opacity = getExpression(values, "stroke-opacity", i);
        if (opacity != null) {
            strokeBuilder.opacity(opacity);
        }
        Expression width = getMeasureExpression(values, "stroke-width", i, "px");
        if (width != null) {
            strokeBuilder.width(width);
        }
        Expression lineCap = getExpression(values, "stroke-linecap", i);
        if (lineCap != null) {
            strokeBuilder.lineCap(lineCap);
        }
        Expression lineJoin = getExpression(values, "stroke-linejoin", i);
        if (lineJoin != null) {
            strokeBuilder.lineJoin(lineJoin);
        }
        float[] dasharray = getFloatArray(values, "stroke-dasharray", i);
        if (dasharray != null) {
            strokeBuilder.dashArray(dasharray);
        }
        Expression dashOffset = getMeasureExpression(values, "stroke-dashoffset", i, "px");
        if (dashOffset != null) {
            strokeBuilder.dashOffset(dashOffset);
        }

    }

    /**
     * Adds the vendor options available
     * 
     * @param sb
     * @param vendorOptions
     * @param values
     * @param idx
     */
    private void addVendorOptions(SymbolizerBuilder<?> sb, Map<String, String> vendorOptions,
            Map<String, List<Value>> values, int idx) {
        for (Map.Entry<String, String> entry : vendorOptions.entrySet()) {
            String cssKey = entry.getKey();
            String sldKey = entry.getValue();
            String value = getLiteral(values, cssKey, idx, null);
            if (value != null) {
                sb.option(sldKey, value);
            }
        }

    }

    /**
     * Builds a mark into the graphic builder from the idx-th set of property alues
     * 
     * @param markName
     * @param cssRule
     * @param indexedPseudoClass
     * @param idx
     * @param gb
     */
    private void buildMark(Value markName, CssRule cssRule, String indexedPseudoClass, int idx,
            GraphicBuilder gb) {
        MarkBuilder mark = gb.mark();
        mark.name(markName.toExpression());
        // see if we have a pseudo-selector for this idx
        Map<String, List<Value>> values = getValuesForIndexedPseudoClass(cssRule,
                indexedPseudoClass, idx);
        if (values == null || values.isEmpty()) {
            mark.fill().reset();
            mark.stroke().reset();
        } else {
            // unless specified and empty, a mark always has a fill and a stroke
            if (values.containsKey("fill") && values.get("fill") != null) {
                FillBuilder fb = mark.fill();
                buildFill(cssRule, fb, values, idx);
            } else if (!values.containsKey("fill")) {
                mark.fill();
            }

            if (values.containsKey("stroke") && values.get("stroke") != null) {
                StrokeBuilder sb = mark.stroke();
                buildStroke(cssRule, sb, values, idx);
            } else if (!values.containsKey("stroke")) {
                mark.stroke();
            }
        }
        Expression size = getMeasureExpression(values, "size", idx, "px");
        if (size != null) {
            gb.size(size);
        }
        Expression rotation = getMeasureExpression(values, "rotation", idx, "deg");
        if (rotation != null) {
            gb.rotation(rotation);
        }
    }

    /**
     * Returns the set of values for the idx-th pseudo-class taking into account both generic and
     * non indexed pseudo class names
     * 
     * @param cssRule
     * @param pseudoClassName
     * @param idx
     * @return
     */
    private Map<String, List<Value>> getValuesForIndexedPseudoClass(CssRule cssRule,
            String pseudoClassName, int idx) {
        Map<String, List<Value>> combined = new LinkedHashMap<>();
        // catch all ones
        combined.putAll(cssRule.getPropertyValues(PseudoClass.newPseudoClass("symbol")));
        // catch all index specific
        combined.putAll(cssRule.getPropertyValues(PseudoClass.newPseudoClass("symbol", idx + 1)));
        // symbol specific ones
        combined.putAll(cssRule.getPropertyValues(PseudoClass.newPseudoClass(pseudoClassName)));
        // symbol and index specific ones
        combined.putAll(cssRule.getPropertyValues(PseudoClass.newPseudoClass(pseudoClassName,
                idx + 1)));
        return combined;
    }

    /**
     * Builds an expression out of the i-th value
     * 
     * @param valueMap
     * @param name
     * @param i
     * @return
     */
    private Expression getExpression(Map<String, List<Value>> valueMap, String name, int i) {
        Value v = getValue(valueMap, name, i);
        return getExpression(v);
    }

    /**
     * Builds/grabs an expression from the specified value, if a multi value is passed the first
     * value will be used
     * 
     * @param v
     * @return
     */
    private Expression getExpression(Value v) {
        if (v == null) {
            return null;
        } else {
            if (v instanceof MultiValue) {
                return ((MultiValue) v).values.get(0).toExpression();
            } else {
                return v.toExpression();
            }
        }
    }

    /**
     * Returns an expression for the i-th value of the specified property, taking into account units
     * of measure
     * 
     * @param valueMap
     * @param name
     * @param i
     * @param defaultUnit
     * @return
     */
    private Expression getMeasureExpression(Map<String, List<Value>> valueMap, String name, int i,
            String defaultUnit) {
        Value v = getValue(valueMap, name, i);
        if (v == null) {
            return null;
        } else if (v instanceof Literal) {
            String literal = v.toLiteral();
            if (literal.endsWith(defaultUnit)) {
                String simplified = literal.substring(0, literal.length() - defaultUnit.length());
                return FF.literal(simplified);
            } else {
                return FF.literal(literal);
            }
        } else {
            return v.toExpression();
        }
    }

    /**
     * Returns the i-th value of the specified property
     * 
     * @param valueMap
     * @param name
     * @param i
     * @return
     */
    private Value getValue(Map<String, List<Value>> valueMap, String name, int i) {
        List<Value> values = valueMap.get(name);
        if (values == null || values.isEmpty()) {
            return null;
        }

        if (values.size() == 1) {
            return values.get(0);
        } else if (i > values.size()) {
            return null;
        } else {
            return values.get(i);
        }
    }

    private List<Value> getMultiValue(Map<String, List<Value>> valueMap, String name, int i) {
        Value value = getValue(valueMap, name, i);
        if (value instanceof MultiValue) {
            return ((MultiValue) value).values;
        } else {
            return Collections.singletonList(value);
        }
    }

    /**
     * Returns the i-th value of the specified property, as a literal
     * 
     * @param valueMap
     * @param name
     * @param i
     * @param defaultValue
     * @return
     */
    private String getLiteral(Map<String, List<Value>> valueMap, String name, int i,
            String defaultValue) {
        Value v = getValue(valueMap, name, i);
        if (v == null) {
            return defaultValue;
        } else {
            return v.toLiteral();
        }
    }

    /**
     * Returns the i-th value of the specified property, as a array of floats
     * 
     * @param valueMap
     * @param name
     * @param i
     * @return
     */
    private float[] getFloatArray(Map<String, List<Value>> valueMap, String name, int i) {
        double[] doubles = getDoubleArray(valueMap, name, i);
        if (doubles == null) {
            return null;
        } else {
            float[] floats = new float[doubles.length];
            for (int j = 0; j < doubles.length; j++) {
                floats[j] = (float) doubles[j];
            }
            return floats;
        }
    }

    /**
     * Returns the i-th value of the specified property, as a array of doubles
     * 
     * @param valueMap
     * @param name
     * @param i
     * @return
     */
    private double[] getDoubleArray(Map<String, List<Value>> valueMap, String name, int i) {
        Value v = getValue(valueMap, name, i);
        if (v == null) {
            return null;
        }
        if (v instanceof MultiValue) {
            MultiValue m = (MultiValue) v;
            if (m.values.size() == 0) {
                return null;
            }
            double[] result = new double[m.values.size()];
            for (int j = 0; j < m.values.size(); j++) {
                String literal = m.values.get(j).toLiteral();
                if (literal.endsWith("%")) {
                    literal = literal.substring(0, literal.length() - 1);
                    double d = Double.parseDouble(literal);
                    result[j] = d / 100d;
                } else {
                    result[j] = Double.parseDouble(literal);
                }
            }
            return result;

        } else {
            return new double[] { Double.parseDouble(v.toLiteral()) };
        }
    }

    /**
     * Returns the i-th value of the specified property, as a array of strings
     * 
     * @param valueMap
     * @param name
     * @param i
     * @return
     */
    private String[] getStringArray(Map<String, List<Value>> valueMap, String name, int i) {
        Value v = getValue(valueMap, name, i);
        if (v == null) {
            return null;
        }
        if (v instanceof MultiValue) {
            MultiValue m = (MultiValue) v;
            if (m.values.size() == 0) {
                return null;
            }
            String[] result = new String[m.values.size()];
            for (int j = 0; j < m.values.size(); j++) {
                result[j] = m.values.get(j).toLiteral();
            }
            return result;

        } else {
            return new String[] { v.toLiteral() };
        }
    }

    /**
     * Returns the max number of property values in the provided property set (for repeated
     * symbolizers)
     * 
     * @param valueMap
     * @return
     */
    private int getMaxRepeatCount(Map<String, List<Value>> valueMap) {
        int max = 1;
        for (List<Value> values : valueMap.values()) {
            max = Math.max(max, values.size());
        }

        return max;
    }

    public static void main(String[] args) throws IOException, TransformerException {
        if (args.length != 2) {
            System.err.println("Usage: CssTranslator <input.css> <output.sld>");
            System.exit(-1);
        }
        File input = new File(args[0]);
        if (!input.exists()) {
            System.err.println("Could not locate input file " + input.getPath());
            System.exit(-2);
        }
        File output = new File(args[1]);
        File outputParent = output.getParentFile();
        if (!outputParent.exists() && !outputParent.mkdirs()) {
            System.err
                    .println("Output file parent directory does not exist, and cannot be created: "
                            + outputParent.getPath());
            System.exit(-2);
        }
        CssParser parser = Parboiled.createParser(CssParser.class);
        String css = FileUtils.readFileToString(input);

        long start = System.currentTimeMillis();
        ParsingResult<Stylesheet> result = new ReportingParseRunner<Stylesheet>(parser.StyleSheet())
                .run(css);
        if (result.hasErrors()) {
            System.err.println(ErrorUtils.printParseErrors(result));
            System.exit(-3);
        }

        Stylesheet ss = result.parseTreeRoot.getValue();
        CssTranslator translator = new CssTranslator();
        Style style = translator.translate(ss);

        StyleFactory styleFactory = CommonFactoryFinder.getStyleFactory();
        StyledLayerDescriptor sld = styleFactory.createStyledLayerDescriptor();
        NamedLayer layer = styleFactory.createNamedLayer();
        layer.addStyle((org.geotools.styling.Style) style);
        sld.layers().add(layer);
        SLDTransformer tx = new SLDTransformer();
        tx.setIndentation(2);
        try (FileOutputStream fos = new FileOutputStream(output)) {
            tx.transform(sld, fos);
        }
        long end = System.currentTimeMillis();

        System.out.println("Translation performed in " + (end - start) / 1000d + " seconds");
    }

}