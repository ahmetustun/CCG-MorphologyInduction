
import org.deeplearning4j.models.embeddings.loader.WordVectorSerializer;
import org.deeplearning4j.models.embeddings.wordvectors.WordVectors;

import java.io.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.StringTokenizer;

/**
 * Created by ahmet on 21/01/2017.
 */
public class CCGMorphologyLearner {

    public static WordVectors vectors;
    public static double threshold = 0.25;

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

    public static ArrayList<String> getPossibleSplits(String word, String lemma, int suffixNo) throws FileNotFoundException {

        ArrayList<String> pSegmentations = new ArrayList<String>();
        getPossibleAffixSequence(lemma, word.substring(lemma.length()), pSegmentations, suffixNo, 1);

        ArrayList<String> fSegmentations = new ArrayList<String>();
        for (String s : pSegmentations) {
            StringTokenizer st = new StringTokenizer(s, " ");
            String curr = st.nextToken() + st.nextToken();
            String next = "";
            boolean ok = true;
            while (st.hasMoreTokens()) {
                next = curr + st.nextToken();
                if (vectors.hasWord(curr) && vectors.hasWord(next) && (vectors.similarity(curr, next) > threshold && vectors.similarity(curr, next) < 1)) {
                    curr = next;
                } else {
                    ok = false;
                    break;
                }
            }
            if (ok) {
                fSegmentations.add(s);
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

    public static String getLexcialEntry(String suffix, String[] semantics, String pos) {

        String tmp = "% " + suffix + "\n";

        if (pos.equalsIgnoreCase("n")) {
            for (String meaning : semantics) {
                tmp = tmp + suffix + " s := np/np: \\x.!" + meaning + " x;" + "\n"
                        + suffix + " s := np\\np: \\x.!" + meaning + " x;" + "\n\n";
            }
        } else if (pos.equalsIgnoreCase("v")) {
            for (String meaning : semantics) {
                tmp = tmp + suffix + " s := vp/vp: \\x.!" + meaning + " x;" + "\n"
                        + suffix + " s := vp\\vp: \\x.!" + meaning + " x;" + "\n\n";
            }
        }

        return tmp;
    }

    /* ================================ Lexicon and Training Set Generation =========================================== */

    public static void generateCCGandSUP(String inFile) throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(inFile));
        FileWriter ccg = new FileWriter(inFile + ".ccg");
        FileWriter sup = new FileWriter(inFile + ".sup");

        HashSet<String> suffixes = new HashSet<>();
        String supString = "(\n";

        String line;
        while ((line = reader.readLine()) != null) {

            StringTokenizer st = new StringTokenizer(line, " ");

            String lemma = st.nextToken();
            String pos = st.nextToken();
            String mor = st.nextToken();
            String word = st.nextToken();

            StringTokenizer tokenizer = new StringTokenizer(mor, ",");
            String[] tags = mor.split(",");
            int suffixNo = tags.length;

            for (String s : getPossibleSplits(word, lemma, suffixNo)) {
                StringTokenizer tokens = new StringTokenizer(s, " ");
                tokens.nextToken();
                while (tokens.hasMoreTokens()) {
                    String suff = tokens.nextToken();
                    ccg.write(getLexcialEntry(suff, tags, pos));
                }

                supString = supString + "((" + s + ") " + getLF(lemma, mor) + ")\n";
            }
        }

        supString = supString + ")";
        sup.write(supString);

        sup.close();
        ccg.close();
    }

    /* ================================ Main =========================================== */

    public static void main(String[] args) throws IOException {
        loadVectors(args[1]);
        generateCCGandSUP(args[2]);
    }
}
