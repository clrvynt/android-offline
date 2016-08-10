package com.kk.noteservice;


public interface NoteCallback<T> {
    void onResponse(T response);

    void onError(int code, String message);
}
