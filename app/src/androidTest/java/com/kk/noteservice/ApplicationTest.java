package com.kk.noteservice;

import android.content.Context;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import android.util.Log;

import com.google.gson.Gson;
import com.kk.noteservice.model.Note;
import com.kk.noteservice.util.NoteDB;

import junit.framework.Assert;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import okhttp3.HttpUrl;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(AndroidJUnit4.class)
public class ApplicationTest {

    private NoteService noteService;
    private MockWebServer server;
    @Before
    public void before() throws Exception {
        Context c = InstrumentationRegistry.getInstrumentation().getTargetContext();
        NoteDB.init(c);

        server = new MockWebServer();
        server.start();
        HttpUrl url = server.url("");
        noteService = NoteServiceImpl.getInstance(c, url.toString());
    }

    @After
    public void after() throws Exception {
        server.shutdown();
    }

    @Test
    public void testLogin() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        server.enqueue(new MockResponse().setBody("Foo"));
        noteService.login("foo@bar.com", "password", new NoteCallback<String>() {
            @Override
            public void onResponse(String response) {
                Assert.assertEquals("Foo", response);
                latch.countDown();
            }

            @Override
            public void onError(int code, String message) {
                fail("Test failed");
                latch.countDown();
            }
        });
        latch.await(5, TimeUnit.SECONDS);
    }

    @Test
    public void testCreateNote() throws Exception {
        final CountDownLatch latch = new CountDownLatch(2);
        final Note n = makeNote();
        server.enqueue(new MockResponse().addHeader("Content-Type", "application/json; charset=utf-8").setBody(new Gson().toJson(n).toString()));
        noteService.createNote(n, UUID.randomUUID().toString(), new NoteCallback<Note>() {
            @Override
            public void onResponse(Note response) {
                assertTrue(response.getId().equals(n.getId()));
                // Now fetch from the database and check if it matches.
                noteService.loadNotes(new NoteCallback<List<Note>>() {
                    @Override
                    public void onResponse(List<Note> response) {
                        Log.d("DEBUG", response.toString());
                        assertTrue(response.contains(n));
                        try {
                            Thread.sleep(2000);
                        }
                        catch (InterruptedException e) {

                        }
                        latch.countDown();
                    }

                    @Override
                    public void onError(int code, String message) {
                        fail("Loading from the database failed");
                        latch.countDown();
                    }
                });
                latch.countDown();
            }

            @Override
            public void onError(int code, String message) {
                fail("Mock Server call failed");
                latch.countDown();
            }
        });
        latch.await(5, TimeUnit.SECONDS);
    }

    private Note makeNote() {
        Note n = new Note();
        n.setId(UUID.randomUUID().toString());
        n.setUserId(UUID.randomUUID().toString());
        n.setTitle("Random Title");
        n.setDescription("Random Description");
        n.setCreatedDate(new Date());

        return n;
    }

}