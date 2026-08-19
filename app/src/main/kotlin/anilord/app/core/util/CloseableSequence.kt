package anilord.app.core.util

interface CloseableSequence<T> : Sequence<T>, AutoCloseable
