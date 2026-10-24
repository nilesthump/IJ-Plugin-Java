class CaptureSample {
    private int multiplier = 2;

    int twice(int value) {
        return value * multiplier;
    }

    void unrelated() {
        System.out.println("UNRELATED_BODY_SHOULD_NOT_BE_CAPTURED");
    }
}
