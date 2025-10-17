package edu.mceserani;

import java.io.*;
import java.util.ArrayList;
import java.util.Comparator;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntArrays;

/**
 * Punto A1 + A2: 
 *  - A1: lettura di name.basics.tsv e costruzione della mappa Int2ObjectOpenHashMap<Integer,Attore>
 *  - A2: scansione di title.principals.tsv e costruzione del grafo (set di coprotagonisti per attore)
 *
 * Uso (A1+A2):
 *   javac -cp fastutil.jar CreaGrafo.java
 *   java  -cp .:fastutil.jar CreaGrafo ../data/name.basics.tsv ../data/title.principals.tsv
 */
public class GrafoAttori {

    // ===================== Modello dati (A1) =====================
    public static final class Attore {
        public final int codice;   // codice attore come int (da nm...)
        public final String nome;  // primaryName
        public final int anno;     // birthYear
        // In A2 useremo questo set per accumulare i coprotagonisti (evita duplicati).
        public final IntSet co;    // insieme di codici attori adiacenti (temporaneo)
        public int[] adj;     // adiacenze finali ordinate (riempite in A3)
        Attore(int codice, String nome, int anno) {
            this.codice = codice;
            this.nome = nome;
            this.anno = anno;
            this.co = new IntOpenHashSet();
        }
    }

    // ===================== A2: costruzione del grafo da title.principals.tsv =====================
    /**
     * Scansione singola di title.principals.tsv. Per ogni titolo raccoglie il cast degli attori già
     * selezionati (presenti in attoriByCodice) e collega tutte le coppie (u,v) simmetricamente.
     * Il buffer cast è riutilizzato tra un titolo e l'altro. Dedup finale per sicurezza.
     */
    static void buildGraphFromTitlePrincipals(String pathPrincipals, Int2ObjectOpenHashMap<Attore> attoriByCodice) throws Exception {
        try (BufferedReader br = new BufferedReader(new FileReader(pathPrincipals))) {
            String line = br.readLine(); // header
            String prevT = null;
            IntArrayList cast = new IntArrayList(32); // buffer riutilizzabile per il cast del titolo corrente

            // Funzione locale: al cambio titolo, collega le coppie del cast
            java.util.function.Consumer<IntArrayList> flushTitle = (lst) -> {
                if (lst.isEmpty()) return;
                int[] a = lst.elements();
                int n = lst.size();
                // ordina e deduplica in-place
                IntArrays.quickSort(a, 0, n);
                int w = 0;
                for (int i = 0; i < n; i++) {
                    if (i == 0 || a[i] != a[i - 1]) a[w++] = a[i];
                }
                lst.size(w);
                // collega tutte le coppie u<v, inserimento simmetrico
                for (int i = 0; i < w; i++) {
                    int u = a[i];
                    Attore au = attoriByCodice.get(u);
                    if (au == null) continue;
                    for (int j = i + 1; j < w; j++) {
                        int v = a[j];
                        Attore av = attoriByCodice.get(v);
                        if (av == null) continue;
                        au.co.add(v);
                        av.co.add(u);
                    }
                }
                lst.clear();
            };

            while ((line = br.readLine()) != null) {
                String[] f = line.split("\t", -1);
                if (f.length < 3) continue; // riga malformata
                String tconst = f[0];
                String nconst = f[2];

                // flush sul cambio titolo
                if (prevT != null && !tconst.equals(prevT)) {
                    flushTitle.accept(cast);
                }

                int personCode = parseNumericId(nconst);
                if (attoriByCodice.containsKey(personCode)) {
                    cast.add(personCode);
                }
                prevT = tconst;
            }
            // flush ultimo titolo
            flushTitle.accept(cast);
        }
    }

     // ===================== A3: scrittura nomi.tsv e grafo.tsv =====================
    static void writeOutputs(Int2ObjectOpenHashMap<Attore> attoriByCodice, String outDir) throws Exception {
        File out = new File(outDir);
        if (!out.exists()) out.mkdirs();
        File fNomi = new File(out, "nomi.tsv");
        File fGrafo = new File(out, "grafo.tsv");

        // Raccogli attori e ordina per codice
        ArrayList<Attore> list = new ArrayList<>(attoriByCodice.size());
        for (Int2ObjectOpenHashMap.Entry<Attore> e : attoriByCodice.int2ObjectEntrySet()) {
            list.add(e.getValue());
        }
        list.sort(Comparator.comparingInt(a -> a.codice));

        try (PrintWriter pwN = new PrintWriter(new BufferedWriter(new FileWriter(fNomi)));
             PrintWriter pwG = new PrintWriter(new BufferedWriter(new FileWriter(fGrafo)))) {
            for (Attore a : list) {
                // nomi.tsv
                pwN.printf("%d	%s	%d%n", a.codice, a.nome, a.anno);

                // converte il set temporaneo in array ordinato e libera il set
                int[] adj = ((IntOpenHashSet)a.co).toIntArray();
                IntArrays.quickSort(adj);
                a.adj = adj;

                // grafo.tsv
                pwG.print(a.codice);
                pwG.print('	');
                pwG.print(adj.length);
                for (int v : adj) { pwG.print('	'); pwG.print(v); }
                pwG.println();
            }
        }

        // opzionale: liberare i set per ridurre memoria di picco post-scrittura
        for (Attore a : list) {
            if (a.co != null) { a.co.clear(); }
        }
    }

    // ===================== MAIN (A1+A2) =====================
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Uso: java CreaGrafo <path/name.basics.tsv> <path/title.principals.tsv>");
            System.exit(1);
        }
        String nameBasicsPath = args[0];
        String titlePrincipalsPath = args[1];

        // Preallocazione: ~383k attori previsti → capacity ~512k con LF 0.75
        Int2ObjectOpenHashMap<Attore> attoriByCodice = new Int2ObjectOpenHashMap<>(512_000, 0.75f);

        int nTot = 0, nAttori = 0;
        try (BufferedReader br = new BufferedReader(new FileReader(nameBasicsPath))) {
            String line = br.readLine(); // header
            while ((line = br.readLine()) != null) {
                nTot++;
                String[] f = line.split("\t", -1);
                if (f.length < 5) continue; // riga malformata
                String nconst = f[0];          // es. nm0000148
                String primaryName = f[1];
                String birthYear = f[2];       // es. 1963 oppure \N
                String professions = f[4];     // es. actor,producer

                if (isUnknown(birthYear)) continue; // serve anno noto
                if (!isActorProfession(professions)) continue; // deve contenere actor/actress

                int codice = parseNumericId(nconst); // nm... → int
                int anno = safeParseInt(birthYear);

                Attore a = new Attore(codice, primaryName, anno);
                attoriByCodice.put(codice, a);
                nAttori++;
            }
        }

        long t0 = System.currentTimeMillis();
        buildGraphFromTitlePrincipals(titlePrincipalsPath, attoriByCodice);
        long t1 = System.currentTimeMillis();

        // Log di verifica rapido (A2): conteggio grezzo degli archi (diretti) sommando i gradi
        long sommaGradi = 0;
        int nodiConVicini = 0;
        for (Int2ObjectOpenHashMap.Entry<Attore> e : attoriByCodice.int2ObjectEntrySet()) {
            int deg = e.getValue().co.size();
            sommaGradi += deg;
            if (deg > 0) nodiConVicini++;
        }

        System.out.println("[A1] Righe lette: " + nTot);
        System.out.println("[A1] Attori selezionati: " + nAttori);
        System.out.println("[A2] Costruzione grafo completata in " + (t1 - t0) + " ms");
        System.out.println("[A2] Somma dei gradi (archi diretti): " + sommaGradi);
        System.out.println("[A2] Nodi con almeno un vicino: " + nodiConVicini);
    
        // ===================== A3: finalizzazione e scrittura output =====================
        writeOutputs(attoriByCodice, "../out");
        System.out.println("[A3] Scrittura di out/nomi.tsv e out/grafo.tsv completata.");
    }

    // ===================== Utility parsing =====================
    private static boolean isUnknown(String s) {
        return s == null || s.isEmpty() || "\\N".equals(s);
    }

    private static boolean isActorProfession(String profs) {
        if (profs == null) return false;
        // match semplice (case-sensitive come nei TSV IMDB)
        return profs.contains("actor") || profs.contains("actress");
    }

    /** Estrae la parte numerica da un id IMDB (nmXXXX / ttXXXX). */
    private static int parseNumericId(String imdbId) {
        int n = 0; int len = imdbId.length();
        for (int i = 0; i < len; i++) {
            char ch = imdbId.charAt(i);
            if (ch >= '0' && ch <= '9') {
                n = n * 10 + (ch - '0');
            }
        }
        return n;
    }

    private static int safeParseInt(String s) {
        try { return Integer.parseInt(s); } catch (Exception e) { return -1; }
    }
}
