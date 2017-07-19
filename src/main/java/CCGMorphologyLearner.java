
import org.deeplearning4j.models.embeddings.loader.WordVectorSerializer;
import org.deeplearning4j.models.embeddings.wordvectors.WordVectors;

import java.io.*;
import java.util.*;

/**
 * Created by ahmet on 21/01/2017.
 */
public class CCGMorphologyLearner {

    public static WordVectors vectors;
    public static double threshold = 0.3;

    public static boolean setLimit = true;
    public static int limit = 2;

    public static int corpLimit = 2;

    public static Set<String> lemmaANDpos = new TreeSet<>();
    public static Set<String> morphemeEntry = new TreeSet<>();
    public static Set<String> supEntry = new HashSet<>();

    /* ================================ Vector Loading =========================================== */

    public static void loadVectors(String vectorFile) throws FileNotFoundException {
        vectors = WordVectorSerializer.loadTxtVectors(new File(vectorFile));
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

            String root = lemma+"#"+pos;

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
                for (int i=0; i<corpLimit; i++) {
                    writer.write(words.get(i) + "\n");
                }
            }
        }

        writer.close();
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

    /* ================================ Segmentation =========================================== */

    public static ArrayList<String> getPossibleSplits(String word, String lemma, int suffixNo, String pos) throws FileNotFoundException {

        String lemma_t = word.substring(0, lemma.length());

        ArrayList<String> pSegmentations = new ArrayList<String>();
        getPossibleAffixSequence(lemma_t, word.substring(lemma.length()), pSegmentations, suffixNo, 1);

        ArrayList<String> fSegmentations = new ArrayList<String>();
        TreeMap<Double, String> score2seg = new TreeMap<Double, String>();
        for (String s : pSegmentations) {
            StringTokenizer st = new StringTokenizer(s, " ");

            String curr = "";
            String root = "";
            if (pos.equalsIgnoreCase("V")) {
                curr = st.nextToken() + st.nextToken();
                root = curr;
            } else {
                curr = st.nextToken();
                root = lemma;
            }

            String next = "";
            boolean ok = true;
            double score = 0d;
            while (st.hasMoreTokens()) {
                next = curr + st.nextToken();
                double distance = vectors.similarity(root, next);
                if (vectors.hasWord(root) && vectors.hasWord(next) && (distance > threshold && vectors.similarity(root, next) < 1)) {
                    curr = next;
                    root = next;
                    score = score + distance;
                } else {
                    ok = false;
                    break;
                }
            }
            if (ok) {
                score2seg.put(score, s);
//                fSegmentations.add(s);
            }
        }
/*
        ArrayList<String> sSegmentations = new ArrayList<String>();
        if (fSegmentations.isEmpty()) {
            for (String s : pSegmentations) {
                StringTokenizer st = new StringTokenizer(s, " ");

                String curr;
                String root;

//                if (pos.equalsIgnoreCase("V")) {
//                    curr = st.nextToken() + st.nextToken();
//                    root = curr;
//                } else {

                curr = st.nextToken();
                root = lemma;
//                }

                String next = "";
                boolean ok = true;
                while (st.hasMoreTokens()) {
                    next = curr + st.nextToken();
                    if (vectors.hasWord(root) && vectors.hasWord(next)) {
                        curr = next;
                        root = next;
                    } else {
                        ok = false;
                        break;
                    }
                }
                if (ok) {
                    sSegmentations.add(s);
                }
            }
        } else return fSegmentations;

        if (sSegmentations.isEmpty()) return pSegmentations;
*/
        if (setLimit && score2seg.size() > limit) {
            double key = score2seg.lastKey();
            for (int i = 0; i < limit; i++) {
                fSegmentations.add(score2seg.get(key));
                key = score2seg.lowerKey(key);
            }
        } else {
            for (double key : score2seg.keySet()) {
                fSegmentations.add(score2seg.get(key));
            }
        }

        return fSegmentations;
    }

    private static void getPossibleAffixSequence(String head, String tail, List<String> segmentations, int suffixNo, int level) {
        if (suffixNo == level) {
            segmentations.add(head + " " + tail);
        } else if (tail.length() == 0) {
            segmentations.add(head);
        } else {
            for (int i = 1; i < tail.length() + 1; i++) {
                String morpheme = tail.substring(0, i);

                if (morpheme.length() != tail.length()) {
                    String tailMorph = tail.substring(i);
                    String headMorph = head + " " + morpheme;
                    getPossibleAffixSequence(headMorph, tailMorph, segmentations, suffixNo, level + 1);
                }
            }
        }
    }

    /* ================================ Lexicon Operation with Templates =========================================== */

    public static void getLexicalEntryForAffixes(String suffix, String[] semantics, String pos) {

        if (pos.equalsIgnoreCase("n")) {
            for (String meaning : semantics) {
                morphemeEntry.add(suffix + " s := np/np: \\x.!" + meaning + " x;\n");
                morphemeEntry.add(suffix + " s := np\\np: \\x.!" + meaning + " x;\n");
            }
        } else if (pos.equalsIgnoreCase("v")) {
            for (String meaning : semantics) {
                morphemeEntry.add(suffix + " s := vp/vp: \\x.!" + meaning + " x;\n");
                morphemeEntry.add(suffix + " s := vp\\vp: \\x.!" + meaning + " x;\n");
            }
        }
    }

    public static int lemmaNo = 0;

    public static String getLexicalEntryForLemma(String lemma, String pos) {
        //String lf = pos + lemmaNo;
        //lemmaNo++;

        String lf = lemma;

        String tmp = lemma + " " + pos + " := " + pos + "p: !" + lf + ";\n";

        return tmp;
    }

    /* ================================ Lexicon and Training Set Generation =========================================== */

    public static void morphoGenLex(String inFile) throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(inFile));

        String supString = "(\n";

        String line;
        while ((line = reader.readLine()) != null) {

            StringTokenizer st = new StringTokenizer(line, " ");

            String lemma = st.nextToken();
            String pos = st.nextToken();
            String mor = st.nextToken();
            String word = st.nextToken();

            lemmaANDpos.add(lemma + "#" + pos.toLowerCase());

            StringTokenizer tokenizer = new StringTokenizer(mor, ",");
            String[] tags = mor.split(",");
            int suffixNo = tags.length;

            for (String s : getPossibleSplits(word, lemma, suffixNo, pos)) {
                StringTokenizer tokens = new StringTokenizer(s, " ");

                String lemma_in = tokens.nextToken();
                lemmaANDpos.add(lemma_in + "#" + pos.toLowerCase());

                while (tokens.hasMoreTokens()) {
                    String suff = tokens.nextToken();

                    getLexicalEntryForAffixes(suff, tags, pos);
                }

                supString = supString + "((" + s + ") " +
                        getLF(lemma_in.toUpperCase(new Locale("tr","TR")).
                                replace("I","ı").
                                replace("İ","I"), mor) + ")\n";
            }
        }

        supString = supString + ")";

        supEntry.add(supString);

    }

    public static void writeToFiles(String inFile) throws IOException {

        FileWriter ccg = new FileWriter(inFile + ".ccg");
        FileWriter sup = new FileWriter(inFile + ".sup");

        ccg.write("% Lemmas\n");
        for (String lemma : lemmaANDpos) {
            String[] lAe = lemma.split("#");
            ccg.write(getLexicalEntryForLemma(lAe[0], lAe[1]));
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
    }

    /* ================================ Main =========================================== */

    public static void main(String[] args) throws IOException {
        loadVectors("/Users/ahmet/Desktop/MorphologySoftware/word2vec/turkce");
        System.out.println("========== Vector file is loaded ==========");

        /*
        cleanData(args[1]);
        System.out.println("========== Training data is cleaned ==========");


        cropData(data + ".clean");
        System.out.println("========== Training data is cropped ==========");
        */

        String data = "/Users/ahmet/Desktop/thesis-work/data/data-clean-limited-segmentations/sigmorphon-modified-train";

        morphoGenLex(data + ".clean");
        System.out.println("========== Morphological-GenLex operation is finished ==========");

        writeToFiles(data);
        System.out.println("========== ccg and sup file is created ==========");

    }
}
