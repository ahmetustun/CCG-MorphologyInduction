import java.util.ArrayList;
import java.util.List;

public class Utils {

    public static void getPossibleAffixSequence(String head, String tail, List<String> segmentations, int suffixNo, int level) {
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

    public static List<String> getAllSplits(String word, int morphemeNo) {

        List<String> pSegmentations = new ArrayList<String>();

        String stem = "";
        for (int i = 2; i < word.length(); i++) {
            stem = word.substring(0,i);
            getPossibleAffixSequence(stem, word.substring(stem.length()), pSegmentations, morphemeNo-1, 1);
        }
        return pSegmentations;
    }

    public static void main(String[] args) {

        String[] test = {"yaptılar"};

        for (String word : test) {
            List<String> results = getAllSplits(word, 3);

            for (String res : results) {
                System.out.println(res);
            }
        }
    }

}
