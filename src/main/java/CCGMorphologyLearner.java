
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
    public static double threshold = 0.4;
    public static String[] nLogicalCategories = {"NPL", "LOC", "DAT", "ACC", "GEN", "P1SG", "P2SG", "P3SG", "P1PL", "P2PL", "P3PL"};
    public static String[] vLogicalCategories = {"PAST", "PROG", "FUT", "AOR", "NARR", "A1SG", "A2SG", "A1PL", "A2PL", "A3PL"};
    public static String[] LogicalCategories = {"NPL", "LOC", "DAT", "ACC", "GEN", "P1SG", "P2SG", "P3SG", "P1PL", "P2PL",
        "P3PL", "PAST", "PROG", "FUT", "AOR", "NARR", "A1SG", "A2SG", "A1PL", "A2PL", "A3PL"};

    static {
        try {
            vectors = WordVectorSerializer.loadTxtVectors(new File("/home/master/Desktop/morph-pro/vectors/turkce.txt"));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    public static String getLF(String wordTags) {
        String lf = "";

        String cWordTag = wordTags.toUpperCase();
        String[] tags = cWordTag.split("\\+");

        int n = tags.length;
        for (int i = 0; i < n - 1; i++) {
            lf = lf + "(\"" + tags[tags.length - 1 - i] + "\" ";
        }

        String cp = "";
        for (int i = 0; i < n - 1; i++) {
            cp = cp + ")";
        }

        lf = lf + tags[0] + cp;
        return lf;
    }

    public static ArrayList<String> getPossibleSplits(String word, String stem, int suffixNo) throws FileNotFoundException {

        ArrayList<String> pSegmentations = new ArrayList<String>();
        getPossibleAffixSequence(stem, word.substring(stem.length()), pSegmentations, suffixNo, 1);

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
//        } else if (tail.length() == 1) {
//            segmentations.add(head + " " + tail);
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

    public static String getLexcialEntry(String suffix, String[] semantics) {

        String tmp = "% " + suffix + "\n";

        for (String meaning : semantics) {

            tmp = tmp + suffix + " s := np/np: \\x.!" + meaning + " x;" + "\n"
                    + suffix + " s := np\\np: \\x.!" + meaning + " x;" + "\n"
                    + suffix + " s := vp/vp: \\x.!" + meaning + " x;" + "\n"
                    + suffix + " s := vp\\vp: \\x.!" + meaning + " x;" + "\n\n";

//            tmp = tmp + suffix + " s := np/np: \\x.!" + meaning + " x;" + "\n" +
//                    suffix + " s := np\\np: \\x.!" + meaning + " x;" + "\n\n";
        }

        return tmp;
    }

    public static void main(String[] args) throws IOException {

        String nFile = "/home/master/Desktop/ccg_project/final/nouns_f";
        String vFile = "/home/master/Desktop/ccg_project/final/verbs_f";

        BufferedReader reader = new BufferedReader(new FileReader(vFile));
        FileWriter ccg = new FileWriter(nFile + ".ccg");
        FileWriter sup = new FileWriter(nFile + ".sup");

        HashSet<String> suffixes = new HashSet<>();
        String supString = "(\n";

        String line;
        while ((line = reader.readLine()) != null) {

            StringTokenizer st = new StringTokenizer(line, " ");
            String word = st.nextToken();
            String stem = st.nextToken();
            String mor = st.nextToken();

            StringTokenizer tokenizer = new StringTokenizer(mor, "+");
            int suffixNo = tokenizer.countTokens();

            for (String s : getPossibleSplits(word, stem, suffixNo - 1)) {
                StringTokenizer tokens = new StringTokenizer(s, " ");
                tokens.nextToken();
                while (tokens.hasMoreTokens()) {
                    suffixes.add(tokens.nextToken());
                }

                supString = supString + "((" + s + ") " + getLF(mor) + ")\n";
            }
        }

        supString = supString + ")";

        sup.write(supString);
        sup.close();

        for (String l : suffixes) {
            ccg.write(getLexcialEntry(l, nLogicalCategories));
        }
        ccg.close();
    }

}
