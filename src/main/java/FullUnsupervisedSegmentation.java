import org.deeplearning4j.models.embeddings.loader.WordVectorSerializer;
import org.deeplearning4j.models.embeddings.wordvectors.WordVectors;

import java.io.*;
import java.util.*;

/**
 * Created by ahmet on 02/04/2017.
 */
public class FullUnsupervisedSegmentation {

    private static String lang = "tr";

    /*
    static {
        try {
            vectors = WordVectorSerializer.loadGoogleModel(new File("/Users/ahmetustun/Desktop/nlp-tools/google_vec.bin"), true);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    public static List<String> getPossibleSplitsByStem(String word, String stem, int suffixNo) throws FileNotFoundException {

        List<String> pSegmentations = new ArrayList<String>();
        Utils.getPossibleAffixSequence(stem, word.substring(stem.length()), pSegmentations, suffixNo, 1);

        List<String> fSegmentations = new ArrayList<String>();
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

    */

    public static List<String> getAllPossibleSplits(String word, int morphemeNo, double threshold, WordVectors vectors, String pos, int max) {

        List<String> tmpResults = new ArrayList<>();

        String tWord = word;

        String[] sWord = new String[2];

        boolean split = false;
        if (word.contains("'")) {
            sWord = word.split("'");
            tWord = sWord[0];
            morphemeNo = morphemeNo - 1;
            split = true;
        }

        if (morphemeNo != 1) {
            tmpResults = Utils.getAllSplits(tWord, morphemeNo);
        } else {
            tmpResults.add(tWord);
        }
        List<String> tResults;
        List<String> results = new ArrayList<>();
        double th = threshold;
        do {
            th = th - 0.05;
            tResults = getSplitByThreshold(tmpResults, th, vectors, pos);
        } while ((tResults.size() == 0) && (th >= 0.19d));


        if (split && sWord.length > 1) {
            for (String res : tResults) {
                results.add(res + " '" + sWord[1]);
            }
        } else if (split) {
            for (String res : tResults) {
                results.add(res + " '");
            }
        } else return Utils.randomSubList(tResults, max);

        return Utils.randomSubList(results, max);
    }

    public static List<String> getSplitByThreshold(List<String> all, double threshold, WordVectors vectors, String pos) {

        List<String> results = new ArrayList<>();

        for (String s : all) {
            StringTokenizer st = new StringTokenizer(s, " ");
            String curr = st.nextToken();

            String next = "";
            boolean isOK = true;

            boolean[] ok = {true, true, true};


            if (pos.equalsIgnoreCase("v") && lang.equalsIgnoreCase("tr")) {
                String[] mastar = {"mek", "mak", ""};
                next = curr + st.nextToken();
                for (int i = 0; i < mastar.length; i++) {
                    ok[i] = (vectors.hasWord(curr + mastar[i]) &&
                            vectors.hasWord(next) &&
                            (vectors.similarity(curr + mastar[i], next) > threshold &&
                                    vectors.similarity(curr + mastar[i], next) < 1));
                }
                curr = next;
            }

            while (st.hasMoreTokens()) {
                next = curr + st.nextToken();
                if (!(isOK = (vectors.hasWord(curr) &&
                        vectors.hasWord(next) &&
                        (vectors.similarity(curr, next) > threshold &&
                                vectors.similarity(curr, next) < 1)))) {
                    break;
                }
                curr = next;
            }
            if (isOK && (ok[0] || ok[1] || ok[2])) results.add(s);
        }
        return results;
    }

/*
    private static String doNested(String word, double threshold) {

        Stack<String> localSuffixes = new Stack<String>();
        String stem = word;

        int count = 0;
        for (int i = 0; i < word.length() - 2; i++) {

            String candidate = stem.substring(0, stem.length() - count);


            if (vectors.hasWord(stem) && vectors.hasWord(candidate) && (vectors.similarity(stem, candidate) > threshold && vectors.similarity(stem, candidate) < 1)) {
                String affix = stem.substring(stem.length() - count, stem.length());

                localSuffixes.push(affix);

                stem = candidate;
                count = 0;
            }
            count = count + 1;
        }

        String result = stem;
        int suffixNo = localSuffixes.size();
        for (int j = 0; j < suffixNo; j++) {
            result = result + "+" + localSuffixes.pop();
        }

        return result;
    }

    public static void main(String[] args) throws IOException {

        String[] test2 = {"beginner's"};
        String[] test3 = {"beauticians'", "recommendations'"};

        for (String word : test3) {
            System.out.println("==========================================================");
            for (String s : getAllPossibleSplits(word, 3)) {
                System.out.println(s);
            }
        }

        for (String word : test2) {
            System.out.println("==========================================================");
            for (String s : getAllPossibleSplits(word, 2)) {
                System.out.println(s);
            }
        }
    }
*/
}
