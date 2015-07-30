package io.scaledml.avito;

import com.google.common.base.Splitter;
import io.scaledml.core.SparseItem;
import io.scaledml.core.inputformats.AbstractDelimiterSeparatedValuesFormat;
import io.scaledml.core.util.LineBytesBuffer;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.tartarus.snowball.SnowballStemmer;
import org.tartarus.snowball.ext.russianStemmer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AvitoInputFormat extends AbstractDelimiterSeparatedValuesFormat {
    static class Location {
        final int level;
        final int regionId;
        final int cityId;

        public Location(int level, int regionId, int cityId) {
            this.level = level;
            this.regionId = regionId;
            this.cityId = cityId;
        }
    }
    private final LineBytesBuffer buff = new LineBytesBuffer();
    private final Map<LineBytesBuffer, Location> locations;
    protected boolean hasIds;
    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.S");
    private final SnowballStemmer stemmer = new russianStemmer();

    static final Splitter tabSplt = Splitter.on('\t').omitEmptyStrings();
    public AvitoInputFormat() throws IOException {
        locations = new HashMap<>();
        try (BufferedReader r = Files.newBufferedReader(Paths.get("datasets/raw/Location.tsv"))) {
            String line;
            boolean first = true;
            while ((line = r.readLine()) != null) {
                if (first) {
                    first = false;
                    continue;
                }
                List<String> cols = tabSplt.splitToList(line);
                if (cols.size() == 2) {
                    locations.put(new LineBytesBuffer(cols.get(0)), new Location(1, -1, -1));
                    continue;
                }
                locations.put(new LineBytesBuffer(cols.get(0)), new Location(
                        Integer.parseInt(cols.get(1)),
                        Integer.parseInt(cols.get(2)),
                        Integer.parseInt(cols.get(3))));
            }
        }
        this.hasIds = false;
    }

    enum InputColumn {
        AdLocationID,
        AdCategoryID,
        Params,
        Price,
        Title,
        IsContext,
        SearchDate,
        IPID,
        UserID,
        IsUserLoggedOn,
        SearchQuery,
        SearchLocationID,
        SearchCategoryID,
        SearchParams,
        SearchID,
        AdID,
        Position,
        ObjectType,
        HistCTR
    }

    enum Feature {
        AdCategoryID(),
        Params(),
        ParamsRaw,
        Price(),
        PriceIsZero,
        priceOrder,
        Hour(),
        WeekDay(),
        IPID(),
        UserID(),
        IsUserLoggedOn(),
        SearchLocationID(),
        SearchLocationLevel(),
        SearchLocationRegion(),
        SearchLocationCity(),
        SearchCategoryID(),
        SearchParams(),
        SearchParamsRaw,
        SearchID(),
        AdID(),
        Position(),
        HistCTR();

        final LineBytesBuffer namespace;

        Feature() {
            this.namespace = new LineBytesBuffer(name());
        }
    }

    enum NLPFeature {
        Title,
        SearchQuery;

        final LineBytesBuffer word;
        final LineBytesBuffer bigram;
        final LineBytesBuffer trigram;
        final LineBytesBuffer skipBigram;
        final LineBytesBuffer skipTrigram;
        final LineBytesBuffer fourgram;

        NLPFeature() {
            word = new LineBytesBuffer(name() + "_word");
            bigram = new LineBytesBuffer(name() + "_bigram");
            trigram = new LineBytesBuffer(name() + "_trigram");
            skipBigram = new LineBytesBuffer(name() + "_skip_bigram");
            skipTrigram = new LineBytesBuffer(name() + "_skip_trigram");
            fourgram = new LineBytesBuffer(name() + "_fourgram");
        }
    }

    enum CTRType {
        linear;

        final LineBytesBuffer namespace;

        CTRType() {
            this.namespace = new LineBytesBuffer(name());
        }
    }

    enum NLPType {
        linear;

        final LineBytesBuffer namespace;

        NLPType() {
            this.namespace = new LineBytesBuffer(name());
        }
    }

    @Override
    protected void processColumn(SparseItem item, int colNum, LineBytesBuffer valueBuffer) {
        if (colNum >= InputColumn.values().length) {
            try {
                if (hasIds) {
                    item.id(valueBuffer.toString());
                } else {
                    item.label(Double.parseDouble(valueBuffer.toString()));
                }
            } catch (NumberFormatException e) {
            }
            return;
        }
        final InputColumn column = InputColumn.values()[colNum];
        switch (column) {
            case AdLocationID:
                break;
            case AdCategoryID:
                Feature feature = Feature.AdCategoryID;
                categoricalFeature(item, valueBuffer, feature);
                break;
            case Params:
                categoricalFeature(item, valueBuffer, Feature.ParamsRaw);
                categoricalParamsFeatures(item, valueBuffer, Feature.Params);
                break;
            case Price:
                categoricalFeature(item, valueBuffer, Feature.Price);
                String price = valueBuffer.toAsciiString();
                if (price.equals("0")) {
                    categoricalFeature(item, 1, Feature.PriceIsZero);
                }
                categoricalFeature(item, price.length(), Feature.priceOrder);
                break;
            case Title:
                nlpFeatures(item, valueBuffer, NLPFeature.Title);
                break;
            case IsContext:
                break;
            case SearchDate:
                LocalDateTime date = LocalDateTime.parse(valueBuffer.toString(), formatter);
                int hour = date.getHour();
                categoricalFeature(item, hour, Feature.Hour);
                int dayOfWeek = date.getDayOfWeek().getValue();
                categoricalFeature(item, dayOfWeek, Feature.WeekDay);
                break;
            case IPID:
                categoricalFeature(item, valueBuffer, Feature.IPID);
                break;
            case UserID:
                categoricalFeature(item, valueBuffer, Feature.UserID);
                break;
            case IsUserLoggedOn:
                categoricalFeature(item, valueBuffer, Feature.IsUserLoggedOn);
                break;
            case SearchQuery:
                nlpFeatures(item, valueBuffer, NLPFeature.SearchQuery);
                break;
            case SearchLocationID:
                categoricalFeature(item, valueBuffer, Feature.SearchLocationID);
                Location location = locations.get(valueBuffer);
                if (location != null) {
                    categoricalFeature(item, location.level, Feature.SearchLocationLevel);
                    categoricalFeature(item, location.regionId, Feature.SearchLocationRegion);
                    categoricalFeature(item, location.cityId, Feature.SearchLocationCity);
                }
                break;
            case SearchCategoryID:
                categoricalFeature(item, valueBuffer, Feature.SearchCategoryID);
                break;
            case SearchParams:
                categoricalFeature(item, valueBuffer, Feature.SearchParamsRaw);
                categoricalParamsFeatures(item, valueBuffer, Feature.SearchParams);
                break;
            case SearchID:
                categoricalFeature(item,valueBuffer, Feature.SearchID);
                break;
            case AdID:
                categoricalFeature(item, valueBuffer, Feature.AdID);
                break;
            case Position:
                categoricalFeature(item, valueBuffer, Feature.Position);
                break;
            case ObjectType:
                break;
            case HistCTR:
                double ctr = Double.parseDouble(valueBuffer.toString());
                featuresProcessor.addNumericalFeature(item, Feature.HistCTR.namespace, CTRType.linear.namespace, ctr);
                break;
        }
    }

    Splitter splitter = Splitter.on(Pattern.compile("[ ,_:.?!]")).trimResults();
    ObjectSet<String> setOfWords = new ObjectOpenHashSet<>();
    ObjectSet<String> setOfBigrams = new ObjectOpenHashSet<>();
    ObjectSet<String> setOfTrigrams = new ObjectOpenHashSet<>();
    ObjectSet<String> setOfSkipBigrams = new ObjectOpenHashSet<>();


    private void nlpFeatures(SparseItem item, LineBytesBuffer valueBuffer, NLPFeature feature) {
        setOfWords.clear();
        setOfBigrams.clear();
        setOfSkipBigrams.clear();
        setOfTrigrams.clear();
        String text = valueBuffer.toString();
        String previousWord = null;
        String previous2Word = null;
        for (String word : splitter.split(text)) {
            word = word.replace('ё', 'е').toLowerCase();
            if (!StopWords.STOP_WORDS.contains(word)) {
                stemmer.setCurrent(word);
                if (stemmer.stem()) {
                    word = stemmer.getCurrent();
                }
                if (previousWord != null) {
                    setOfBigrams.add(word + "_" + previousWord);
                }
                if (previous2Word != null) {
                    setOfSkipBigrams.add(previous2Word + "_" + word);
                    setOfTrigrams.add(previous2Word + "_" + previousWord + "_" + word);
                }
                previous2Word = previousWord;
                previousWord = word;
                setOfWords.add(word);
            }
        }
        for (String word : setOfWords) {
            categoricalFeature(item, feature.word, word);
        }
        for (String bigram : setOfBigrams) {
            categoricalFeature(item, feature.bigram, bigram);
        }
        for (String trigram : setOfTrigrams) {
            categoricalFeature(item, feature.trigram, trigram);
        }
        for (String skipBigram : setOfSkipBigrams) {
            categoricalFeature(item, feature.skipBigram, skipBigram);
        }
    }

    private Pattern paramsWordPattern = Pattern.compile("'[^']+'");
    private void categoricalParamsFeatures(SparseItem item, LineBytesBuffer valueBuffer, final Feature feature) {
        final String params = valueBuffer.toString();
        final Matcher matcher = paramsWordPattern.matcher(params);
        while (matcher.find()) {
            categoricalFeature(item, feature.namespace, matcher.group());
        }

    }

    private void categoricalFeature(SparseItem item, LineBytesBuffer valueBuffer, Feature feature) {
        featuresProcessor.addCategoricalFeature(item, feature.namespace, valueBuffer);
    }

    private void categoricalFeature(SparseItem item, int categoryId, Feature feature) {
        buff.clear();
        buff.putInteger(categoryId);
        featuresProcessor.addCategoricalFeature(item, feature.namespace, buff);
    }

    private void categoricalFeature(SparseItem item, LineBytesBuffer namespace, String category) {
        buff.clear();
        buff.putString(category);
        featuresProcessor.addCategoricalFeature(item, namespace, buff);
    }

    @Override
    protected void finalize(SparseItem item) {

    }

    @Override
    protected char csvDelimiter() {
        return '\t';
    }
}
