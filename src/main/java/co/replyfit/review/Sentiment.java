package co.replyfit.review;

public enum Sentiment {
    POSITIVE("긍정"),
    NEUTRAL("중립"),
    NEGATIVE("부정");

    private final String label;

    Sentiment(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
