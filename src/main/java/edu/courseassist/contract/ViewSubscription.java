package edu.courseassist.contract;

/** Closing an observer releases view resources only. It never cancels a business task. */
@FunctionalInterface
public interface ViewSubscription extends AutoCloseable {
    @Override
    void close();
}
