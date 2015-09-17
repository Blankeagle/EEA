package edu.cmu.cs.lti.emd.annotators.classification;

import edu.cmu.cs.lti.collection_reader.TbfEventDataReader;
import edu.cmu.cs.lti.learning.cache.CrfState;
import edu.cmu.cs.lti.learning.feature.FeatureSpecParser;
import edu.cmu.cs.lti.learning.feature.sentence.extractor.SentenceFeatureExtractor;
import edu.cmu.cs.lti.learning.model.FeatureAlphabet;
import edu.cmu.cs.lti.learning.model.FeatureVector;
import edu.cmu.cs.lti.learning.model.RealValueHashFeatureVector;
import edu.cmu.cs.lti.learning.model.WekaModel;
import edu.cmu.cs.lti.script.type.*;
import edu.cmu.cs.lti.uima.annotator.AbstractLoggingAnnotator;
import edu.cmu.cs.lti.uima.util.TokenAlignmentHelper;
import edu.cmu.cs.lti.uima.util.UimaNlpUtils;
import edu.cmu.cs.lti.utils.Configuration;
import gnu.trove.map.TObjectDoubleMap;
import gnu.trove.map.hash.TObjectDoubleHashMap;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.fit.descriptor.ConfigurationParameter;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.javatuples.Pair;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * Date: 9/14/15
 * Time: 3:00 PM
 *
 * @author Zhengzhong Liu
 */
public class RealisTypeAnnotator extends AbstractLoggingAnnotator {
    public static final String PARAM_MODEL_DIRECTORY = "modelDirectory";

    public static final String PARAM_FEATURE_PACKAGE_NAME = "featurePakcageName";

    public static final String PARAM_CONFIG_PATH = "configPath";

    @ConfigurationParameter(name = PARAM_MODEL_DIRECTORY)
    File modelDirectory;

    @ConfigurationParameter(name = PARAM_CONFIG_PATH)
    File configPath;

    @ConfigurationParameter(name = PARAM_FEATURE_PACKAGE_NAME)
    String featurePackageName;

    private static SentenceFeatureExtractor extractor;

    private WekaModel model;

    // TODO read this from cache;
    private FeatureAlphabet alphabet;

    private FeatureVector dummy;

    private TokenAlignmentHelper alignmentHelper = new TokenAlignmentHelper();

    private String goldTokenComponentId = TbfEventDataReader.class.getSimpleName();

    @Override
    public void initialize(UimaContext aContext) throws ResourceInitializationException {
        super.initialize(aContext);
        logger.info("Loading models ...");
        try {
            model = new WekaModel(modelDirectory);
        } catch (Exception e) {
            throw new ResourceInitializationException(e);
        }

        Configuration config = null;
        try {
            config = new Configuration(configPath);
        } catch (IOException e) {
            e.printStackTrace();
        }

        String featureSpec = config.get("edu.cmu.cs.lti.features.realis.spec");

        FeatureSpecParser parser = new FeatureSpecParser(featurePackageName);
        Configuration realisSpec = parser.parseFeatureFunctionSpecs(featureSpec);
        try {
            extractor = new SentenceFeatureExtractor(model.getAlphabet(), config, realisSpec);
        } catch (ClassNotFoundException | NoSuchMethodException | InvocationTargetException | IllegalAccessException
                | InstantiationException e) {
            e.printStackTrace();
        }

        dummy = new RealValueHashFeatureVector(alphabet);

        logger.info("Model loaded");
    }

    @Override
    public void process(JCas aJCas) throws AnalysisEngineProcessException {
        extractor.initWorkspace(aJCas);
        alignmentHelper.loadWord2Stanford(aJCas, goldTokenComponentId);

        JCas goldView = JCasUtil.getView(aJCas, goldStandardViewName, aJCas);

        String documentKey = JCasUtil.selectSingle(aJCas, Article.class).getArticleName();
        CrfState key = new CrfState();
        key.setDocumentKey(documentKey);

        int sentenceId = 0;


        for (StanfordCorenlpSentence sentence : JCasUtil.select(aJCas, StanfordCorenlpSentence.class)) {
            extractor.resetWorkspace(aJCas, sentence);
            key.setSequenceId(sentenceId);

            List<CandidateEventMention> mentions = JCasUtil.selectCovered(goldView, CandidateEventMention.class,
                    sentence.getBegin(), sentence.getEnd());

            for (CandidateEventMention mention : mentions) {
                TObjectDoubleMap<String> rawFeatures = new TObjectDoubleHashMap<>();

                FeatureVector mentionFeatures = new RealValueHashFeatureVector(alphabet);
                int head = extractor.getTokenIndex(UimaNlpUtils.findHeadFromRange(aJCas, mention
                        .getBegin(), mention.getEnd()));
                extractor.extract(head, mentionFeatures, dummy);

                // Do prediction.
                try {
                    Pair<Double, String> prediction = model.classify("", rawFeatures);
                    mention.setPredictedRealis(prediction.getValue1());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private StanfordCorenlpToken getHead(JCas aJCas, int begin, int end) {
        StanfordCorenlpToken head = UimaNlpUtils.findHeadFromRange(aJCas, begin, end);

        if (head == null) {
            head = UimaNlpUtils.findFirstToken(aJCas, begin, end);
        }

        if (head == null) {
            Word word = UimaNlpUtils.findFirstWord(aJCas, begin, end, goldTokenComponentId);
            if (word != null) {
                head = alignmentHelper.getStanfordToken(word);
            }
        }

        return head;
    }
}
