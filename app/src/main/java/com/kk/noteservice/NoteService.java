package com.kk.noteservice;

import com.kk.noteservice.model.Note;
import com.kk.noteservice.model.User;
import java.util.List;

public interface NoteService {

    void createUser(User user, NoteCallback<User> callback);
    void login(String email, String password, NoteCallback<String> callback);
    void createNote(Note note, String authToken, NoteCallback<Note> callback);
    void getNotes(User user, String authToken, NoteCallback<List<Note>> callback);
    void logout(User user, String authToken, NoteCallback<String> callback);
    void loadNotes(NoteCallback<List<Note>> callback);

}
