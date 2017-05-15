
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

    public static Set<String> lemmaANDpos = new TreeSet<>();
    public static Set<String> morphemeEntry = new TreeSet<>();
    public static Set<String> supEntry = new HashSet<>();

    /* ================================ Vector Loading =========================================== */

    public static void loadVectors(String vectorFile) throws FileNotFoundException {
        vectors = WordVectorSerializer.loadTxtVectors(new File(vectorFile));
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
            while (st.hasMoreTokens()) {
                next = curr + st.nextToken();
                if (vectors.hasWord(root) && vectors.hasWord(next) && (vectors.similarity(root, next) > threshold && vectors.similarity(root, next) < 1)) {
                    curr = next;
                    root = next;
                } else {
                    ok = false;
                    break;
                }
            }
            if (ok) {
                fSegmentations.add(s);
            }
        }

        ArrayList<String> sSegmentations = new ArrayList<String>();
        if (fSegmentations.isEmpty()) {
            for (String s : pSegmentations) {
                StringTokenizer st = new StringTokenizer(s, " ");

                String curr;
                String root;
                /*
                if (pos.equalsIgnoreCase("V")) {
                    curr = st.nextToken() + st.nextToken();
                    root = curr;
                } else {
*/
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

        if (!sSegmentations.isEmpty()) return sSegmentations;

        return pSegmentations;
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
        String lf = pos + lemmaNo;
        lemmaNo++;

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
                tokens.nextToken();
                while (tokens.hasMoreTokens()) {
                    String suff = tokens.nextToken();

                    getLexicalEntryForAffixes(suff, tags, pos);
                }

                supString = supString + "((" + s + ") " + getLF(lemma, mor) + ")\n";
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
        loadVectors(args[0]);
        System.out.println("========== Vector File is loaded ==========");

        morphoGenLex(args[1]);
        System.out.println("========== Morphological-GenLex operation is finished ==========");

        writeToFiles(args[1]);
        System.out.println("========== ccg and sup file is created ==========");
    }
}
