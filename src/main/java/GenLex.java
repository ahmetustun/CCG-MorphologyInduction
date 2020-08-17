import java.io.*;
import java.util.*;

/*
Created by Ahmet Üstün
 */

public class GenLex {

    public static Map<Integer, Set<String>> word2pos = new HashMap<>();
    public static Map<Integer, Set<String>> word2tags = new HashMap<>();
    public static Map<Integer, Set<String>> word2seg = new HashMap<>();

    public static Set<String> cats = new TreeSet<>();
    public static Set<String> sups = new TreeSet<>();


    public static void add2map(Map<Integer, Set<String>> map, int key, String value) {
        if (map.containsKey(key)) {
            map.get(key).add(value);
        } else {
            HashSet<String> set = new HashSet<>();
            set.add(value);
            map.put(key, set);
        }
    }

    public static void readTraining(String inFile, boolean addPOS) throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(inFile));

        String line;
        int id = 1;
        while ((line = reader.readLine()) != null) {
            if (line.length() == 1) {
                continue;
            }
            line = clearBrackets(line);
            String[] w2a = line.split("\t");

            add2map(word2tags, id, w2a[1]);
            if ((w2a.length > 2) && addPOS){
                add2map(word2pos, id, w2a[2]);
            } else {
                add2map(word2pos, id, "N");
                add2map(word2pos, id, "V");
            }

            id++;
        }
    }

    public static String clearBrackets(String str){
        return str.replaceAll("\\)\\(", "\t")
                .replaceAll("\\) \\(", "\t")
                .replaceAll("\\)", "")
                .replaceAll("\\(", "")
                .replaceAll("\"", "");
    }

    public static void readSegmentations(String inFile) throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(inFile));

        String line;
        int id = 1;
        while ((line = reader.readLine()) != null) {
            if (line.length() < 2) {
                continue;
            }
            line = clearBrackets(line);
            String[] w2s = line.split("\t");

            for (String i : w2s) {
                add2map(word2seg, id, i);
            }
            id++;
        }
    }

    public static void prepareCCG() {

        /*
        1. atomic categories: x:= !x
        2. stem categories: x:= POS: !x
        3. affix categories: x:= POS|POS: \x.!TAG x
        4. supervision entries:  SEG: !T1 !T2 !T3
         */

        for (int word : word2pos.keySet()) {

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
                        if (seg.split(" ").length == tag.split(" ").length) {

                            //String sup = seg + " : !" + segments[0];

                            // affix categories
                            //String c : tag.split(" ")
                            String[] tags = tag.split(" ");
                            for (int j=0; j<tags.length-1;j++) {
                                for (int i=1; i<segments.length; i++) {

                                    // Affix categories
                                    String afc1 = segments[i] + " p := " + pos + "\\" + pos + ": \\x.!" + tags[j] + " x;";
                                    String afc2 = segments[i] + " p := " + pos + "/" + pos + ": \\x.!" + tags[j] + " x;";
                                    cats.add(afc1);
                                    cats.add(afc2);
                                }
                                //sup = sup + " !" + tags[j];
                            }

                            // supervision entries
                            //sup = sup + ";";
                            //sups.add(sup);
                        }
                    }
                }
            }
        }
    }

    public static void set2file(Set<String> set, String outFile) throws IOException {
        FileWriter writer = new FileWriter(outFile);

        for (String cat : set) {
            writer.write(cat+"\n");
        }
        writer.close();
    }

    public static void main(String[] args) throws IOException {
        // usage <segmentation-file:str> <training-file:str> <use-POS:boolean> <ccg-output:str>
        readSegmentations(args[0]);
        readTraining(args[1], Boolean.parseBoolean(args[2]));
        prepareCCG();
        set2file(cats, args[3]);
    }

}
