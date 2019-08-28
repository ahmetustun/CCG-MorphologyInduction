import java.io.*;
import java.util.*;

public class CCGData {

    public static Map<String, Set<String>> word2pos = new HashMap<>();
    public static Map<String, Set<String>> word2tags = new HashMap<>();
    public static Map<String, Set<String>> word2seg = new HashMap<>();

    public static Set<String> cats = new TreeSet<>();
    public static Set<String> sups = new TreeSet<>();

    public static File replaceInFile(String file) throws IOException {

        File tempFile = File.createTempFile("buffer", ".tmp");
        FileWriter fw = new FileWriter(tempFile);

        Reader fr = new FileReader(file);
        BufferedReader br = new BufferedReader(fr);

        while(br.ready()) {
            fw.write(br.readLine()
                    .replaceAll("I", "ı")
                    .replaceAll("C", "ç")
                    .replaceAll("S", "ş")
                    .replaceAll("G", "ğ")
                    .replaceAll("O", "ö")
                    .replaceAll("U", "ü")
                    .replaceAll(", ", ",") + "\n");
        }

        fw.close();
        br.close();
        fr.close();

        return tempFile;
    }

    public static void add2map(Map<String, Set<String>> map, String key, String value) {
        if (map.containsKey(key)) {
            map.get(key).add(value);
        } else {
            HashSet<String> set = new HashSet<>();
            set.add(value);
            map.put(key, set);
        }
    }

    public static void readTraining(String inFile) throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(inFile));

        String line;
        while ((line = reader.readLine()) != null) {
            String[] w2a = line.split(" ");

            add2map(word2pos, w2a[0], w2a[1]);
            add2map(word2tags, w2a[0], w2a[2]);
        }
    }

    public static void readSegmentations(String inFile) throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(inFile));

        String line;
        while ((line = reader.readLine()) != null) {
            String[] w2s = line.split("\t");
            if (w2s.length > 1) {
                for (String i : w2s[1].split(",")) {
                    add2map(word2seg, w2s[0], i);
                }
            } else {
                add2map(word2seg, w2s[0], w2s[0]);
            }
        }
    }

    public static void set2file(Set<String> set, String outFile) throws IOException {
        FileWriter writer = new FileWriter(outFile);

        for (String cat : set) {
            writer.write(cat.replaceAll("ı", "\\$")+"\n");
        }
        writer.close();
    }

    public static void prepareCCG() {

        /*
        1. atomic categories: x:= !x
        2. stem categories: x:= POS: !x
        3. affix categories: x:= POS|POS: \x.!TAG x
        4. supervision entries:  SEG: !T1 !T2 !T3
         */

        /*
        if (word2pos.keySet().size() != word2seg.keySet().size()) {
            System.out.println("ERROR: Word mismatch between training file and segmentations file");
            System.exit(100);
        }
        */

        for (String word : word2pos.keySet()) {

            for (String pos : word2pos.get(word)) {
                for (String seg : word2seg.get(word)) {

                    String[] segments = seg.split(" ");

                    // atomic categories
                    String ac =  segments[0] + " p := !" + segments[0];
                    cats.add(ac);

                    // Stem categories
                    String sc = segments[0] + " p := " + pos + ": !" + segments[0];
                    cats.add(sc);


                    for (String tag : word2tags.get(word)) {

                        // form-meaning check
                        if (seg.split(" ").length == tag.split(",").length+1) {

                            String sup = seg + " : !" + segments[0];

                            // affix categories
                            for (String c : tag.split(",")) {
                                for (int i=1; i<segments.length; i++) {

                                    // Affix categories
                                    String afc1 = segments[i] + " p := " + pos + "\\" + pos + ": \\x.!" + c + " x;";
                                    String afc2 = segments[i] + " p := " + pos + "/" + pos + ": \\x.!" + c + " x;";
                                    cats.add(afc1);
                                    cats.add(afc2);
                                }
                                sup = sup + " !" + c;
                            }

                            // supervision entries
                            sup = sup + ";";
                            sups.add(sup);
                        }
                    }
                }
            }
        }
    }

    public static void main(String[] args) throws IOException {
        File tempFile1 = replaceInFile("data/tr.hdp.seg/wordlist.tur.mc-segmented-train160K.mc-segmented");
        readSegmentations(tempFile1.getAbsolutePath());
        File tempFile2 = replaceInFile("data/tr.hdp.seg/goldstd_trainset.segments.tur");
        readSegmentations(tempFile2.getAbsolutePath());
        readTraining("data/tr.training/tr.input.txt");
        prepareCCG();
        set2file(cats, "data/tr.hdp.seg/tr.ccg");
        set2file(sups, "data/tr.hdp.seg/tr.supervision");
    }

}
