package com.example.autocomplete.fuzzy;

import org.springframework.stereotype.Component;

@Component
public class EditDistance {
    public int levenshtein(String a, String b) {
        if (a == null) a = "";
        if (b == null) b = "";
        int[][] dp = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) dp[i][0] = i;
        for (int j = 0; j <= b.length(); j++) dp[0][j] = j;
        for (int i = 1; i <= a.length(); i++)
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1), dp[i - 1][j - 1] + cost);
            }
        return dp[a.length()][b.length()];
    }

    /**
     * Wersja ograniczona budżetem. Używa dwóch wierszy zamiast macierzy O(m*n)
     * i kończy obliczenie, gdy cały wiersz przekracza dopuszczalny dystans.
     */
    public int boundedLevenshtein(String a, String b, int maxDistance) {
        if (a == null) a = "";
        if (b == null) b = "";
        if (maxDistance < 0 || Math.abs(a.length() - b.length()) > maxDistance) {
            return maxDistance + 1;
        }
        int[] previous = new int[b.length() + 1];
        int[] current = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) previous[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            current[0] = i;
            int rowMinimum = current[0];
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
                rowMinimum = Math.min(rowMinimum, current[j]);
            }
            if (rowMinimum > maxDistance) return maxDistance + 1;
            int[] swap = previous;
            previous = current;
            current = swap;
        }
        return previous[b.length()];
    }
}
