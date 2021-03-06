
import org.deeplearning4j.models.embeddings.loader.WordVectorSerializer;
import org.deeplearning4j.models.embeddings.wordvectors.WordVectors;

import java.io.*;
import java.util.*;

/**
 * Created by ahmet on 21/01/2017.
 */
public class CCGMorphologyLearner {

    public static WordVectors vectors;
    public static double threshold = 0.55;

    public static boolean setLimit = true;
    public static int limit = 2;

    public static int corpLimit = 2;
    public static Set<String> lemmaANDpos = new TreeSet<>();
    public static Set<String> morphemeEntry = new TreeSet<>();
    public static List<String> segmentations = new ArrayList<>();
    public static Set<String> supEntry = new HashSet<>();

    public static String lang = "tr";

    /* ================================ Vector Loading =========================================== */

    public static void loadVectors(String vectorFile, boolean google) throws IOException {

        if (!google) {
            vectors = WordVectorSerializer.loadTxtVectors(new File(vectorFile));
        } else {
            vectors = WordVectorSerializer.loadGoogleModel(new File(vectorFile), true);
        }
    }

    /* ================================ Pre-Processes on Training Data =========================================== */

    public static void cleanData(String inFile) throws IOException {

        BufferedReader reader = new BufferedReader(new FileReader(inFile));
        FileWriter writer = new FileWriter(inFile + ".clean");

        String line;
        while ((line = reader.readLine()) != null) {

            StringTokenizer st = new StringTokenizer(line, " ");

            String lemma = st.nextToken();
            String pos = st.nextToken();
            String mor = st.nextToken();
            String word = st.nextToken();

            if (vectors.hasWord(word)) {
                writer.write(line + "\n");

            }

        }

        writer.close();
    }

    public static void cropData(String inFile) throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(inFile));
        FileWriter writer = new FileWriter(inFile + ".crop");

        HashMap<String, ArrayList<String>> root2words = new HashMap<>();

        String line;
        while ((line = reader.readLine()) != null) {

            StringTokenizer st = new StringTokenizer(line, " ");

            String lemma = st.nextToken();
            String pos = st.nextToken();
            String mor = st.nextToken();
            String word = st.nextToken();

            String root = lemma + "#" + pos;

            if (root2words.containsKey(root)) {
                ArrayList<String> words = root2words.get(root);
                words.add(line);
                root2words.put(root, words);
            } else {
                ArrayList<String> words = new ArrayList<>();
                words.add(line);
                root2words.put(root, words);
            }

        }

        for (String root : root2words.keySet()) {
            ArrayList<String> words = root2words.get(root);
            if (words.size() > corpLimit) {
                for (int i = 0; i < corpLimit; i++) {
                    writer.write(words.get(i) + "\n");
                }
            }
        }

        writer.close();
    }

    public static void reduceData(String inFile) throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(inFile));
        FileWriter reduced = new FileWriter(inFile + ".reduced");

        HashMap<String, ArrayList<String>> word2inflection = new HashMap<>();

        String line;
        while ((line = reader.readLine()) != null) {

            String word;
            String pos;
            String mor;
            try {
                StringTokenizer st = new StringTokenizer(line, " ");

                word = st.nextToken();
                pos = st.nextToken();
                mor = st.nextToken();
            } catch (Exception e) {
                continue;
            }

            StringTokenizer tokenizer = new StringTokenizer(mor, ",");
            String[] tags = mor.split(",");

            String word_inf = word + "#" + Integer.toString(tags.length);
            if (word2inflection.containsKey(word_inf)) {
                ArrayList<String> inflections = word2inflection.get(word_inf);
                inflections.add(line);
                word2inflection.put(word_inf, inflections);
            } else {
                ArrayList<String> inflections = new ArrayList<>();
                inflections.add(line);
                word2inflection.put(word_inf, inflections);
            }
        }

        Random r = new Random();

        for (String w : word2inflection.keySet()) {
            ArrayList<String> inflections = word2inflection.get(w);
            reduced.write(inflections.get(r.nextInt(inflections.size())) + "\n");
        }

    }

    /* ================================ Logical Form Operation =========================================== */

    public static String getLF(String lemma, String wordTags) {
        String lf = "";

        String[] tags = (lemma + "," + wordTags).split(",");

        int n = tags.length;
        for (int i = 0; i < n - 1; i++) {
            lf = lf + "(\"" + tags[tags.length - 1 - i] + "\" ";
        }

        String cp = "";
        for (int i = 0; i < n - 1; i++) {
            cp = cp + ")";
        }

        lf = lf + "\"" + tags[0] + "\"" + cp;
        return lf;
    }


    /* ================================ Lexicon Operation with Templates =========================================== */

    public static void getLexicalEntryForAffixes(String suffix, String[] semantics, String pos) {

        if (pos.equalsIgnoreCase("n")) {
            for (String meaning : semantics) {
                morphemeEntry.add(suffix + " s := np/*np: \\x.!" + meaning + " x;\n");
                morphemeEntry.add(suffix + " s := np\\*np: \\x.!" + meaning + " x;\n");
            }
        } else if (pos.equalsIgnoreCase("v")) {
            for (String meaning : semantics) {
                morphemeEntry.add(suffix + " s := vp/*vp: \\x.!" + meaning + " x;\n");
                morphemeEntry.add(suffix + " s := vp\\*vp: \\x.!" + meaning + " x;\n");
            }
        }
    }

    public static int lemmaNo = 0;

    public static String getLexicalEntryForLemma(String lemma, String pos, String meaning) {
        //String lf = pos + lemmaNo;
        //lemmaNo++;

        String lf = meaning;

        String tmp = lemma + " " + pos + " := " + pos + "p: !" + lf + ";\n";

        return tmp;
    }

    /* ================================ Lexicon and Training Set Generation =========================================== */

    public static void morphoGenLex(String inFile) throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(inFile));

        String supString = "(\n";

        String line;
        while ((line = reader.readLine()) != null) {

            String word;
            String pos;
            String mor;
            try {
                StringTokenizer st = new StringTokenizer(line, " ");

                word = st.nextToken();
                pos = st.nextToken();
                mor = st.nextToken();
            } catch (Exception e) {
                continue;
            }

            StringTokenizer tokenizer = new StringTokenizer(mor, ",");
            String[] tags = mor.split(",");
            int suffixNo = tags.length;

            for (String s : FullUnsupervisedSegmentation.getAllPossibleSplits(word, suffixNo + 1, threshold, vectors, pos, 4)) {

                segmentations.add(s);

                String[] tokens = s.split(" ");

                String root = tokens[0];

                for (String token : tokens) {
                    lemmaANDpos.add(token + "#" + pos.toLowerCase() + "#" + root);
                    getLexicalEntryForAffixes(token, tags, pos);
                }

                if (lang.equalsIgnoreCase("tr")) {
                    supString = supString + "((" + s + ") " +
                            getLF(tokens[0].toUpperCase(new Locale("tr", "TR")).
                                    replace("I", "ı").
                                    replace("İ", "I"), mor) + ")\n";
                } else {
                    supString = supString + "((" + s + ") " +
                            getLF(tokens[0].toUpperCase().
                                    replace("I", "ı").
                                    replace("İ", "I"), mor) + ")\n";
                }

            }
        }

        supString = supString + ")";

        supEntry.add(supString);

    }

    public static void writeToFiles(String inFile) throws IOException {

        FileWriter ccg = new FileWriter(inFile + ".ccg");
        FileWriter sup = new FileWriter(inFile + ".sup");
        FileWriter seg = new FileWriter(inFile + ".segmentations.txt");

        ccg.write("% Lemmas\n");
        for (String lemma : lemmaANDpos) {
            String[] lAe = lemma.split("#");
            ccg.write(getLexicalEntryForLemma(lAe[0], lAe[1], lAe[2]));
        }

        ccg.write("\n% Affixes\n");
        for (String morpheme : morphemeEntry) {
            ccg.write(morpheme);
        }

        ccg.close();

        for (String training : supEntry) {
            sup.write(training);
        }

        sup.close();

        for (String segmentation : segmentations) {
            seg.write(segmentation + "\n");
        }

        seg.close();
    }

    /* ================================ Main =========================================== */

    private static String TR_VEC = "/Users/ahmetustun/Desktop/nlp-tools/vectors.txt";
    public static String FIN_VEC = "/Users/ahmetustun/Desktop/nlp-tools/fin/42/model.txt";
    public static String EN_VEC = "/Users/ahmetustun/Desktop/nlp-tools/google_vec.bin";

    public static String to_be_reduced_data = "data/fin.training/fin.input.txt";
    public static String training_data = "data/tr.training/tr.input.txt.reduced";
    public static String test_data = "data/input.txt";

    public static void main(String[] args) throws IOException {

//        reduceData(to_be_reduced_data);

        loadVectors(TR_VEC, false);
        System.out.println("========== Vector file is loaded ==========");

        /*
        cleanData(args[1]);
        System.out.println("========== Training data is cleaned ==========");


        cropData(data + ".clean");
        System.out.println("========== Training data is cropped ==========");
        */

        String data = training_data;

        morphoGenLex(data);
        System.out.println("========== Morphological-GenLex operation is finished ==========");

        writeToFiles(data);
        System.out.println("========== ccg and sup file is created ==========");

    }
}
