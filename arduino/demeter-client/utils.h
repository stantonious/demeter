#pragma once

#include <Arduino.h>

String wordWrap(String text, unsigned int lineLength) {
    String result = "";
    String currentLine = "";
    String currentWord = "";

    for (char c : text) {
        if (c == ' ' || c == '\n') {
            if (currentLine.length() + currentWord.length() + (c == ' ' ? 1 : 0) <= lineLength) {
                currentLine += currentWord + (c == ' ' ? " " : "");
            } else {
                result += currentLine + "\n";
                currentLine = currentWord + (c == ' ' ? " " : "");
            }
            currentWord = "";
        } else {
            currentWord += c;
        }
    }

    if (currentLine.length() + currentWord.length() <= lineLength) {
        result += currentLine + currentWord;
    } else {
        result += currentLine + "\n" + currentWord;
    }

    return result;
}

int countLines(String text) {
    int count = 0;
    for (int i = 0; i < text.length(); i++) {
        if (text.charAt(i) == '\n') {
            count++;
        }
    }
    return count + 1; // Add one for the last line
}
