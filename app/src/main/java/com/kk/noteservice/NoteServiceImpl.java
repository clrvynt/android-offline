package com.kk.noteservice;

import android.content.Context;
import android.support.annotation.NonNull;
import android.util.Base64;
import android.util.Log;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.kk.noteservice.model.Note;
import com.kk.noteservice.model.User;
import com.kk.noteservice.util.GsonDateAdapter;
import com.kk.noteservice.util.NoteDB;
import com.kk.noteservice.util.NoteJobManager;
import com.path.android.jobqueue.AsyncAddCallback;
import com.path.android.jobqueue.Job;
import com.path.android.jobqueue.Params;
import com.raizlabs.android.dbflow.config.DatabaseDefinition;
import com.raizlabs.android.dbflow.config.FlowManager;
import com.raizlabs.android.dbflow.sql.language.CursorResult;
import com.raizlabs.android.dbflow.sql.language.Delete;
import com.raizlabs.android.dbflow.sql.language.SQLite;
import com.raizlabs.android.dbflow.structure.database.DatabaseWrapper;
import com.raizlabs.android.dbflow.structure.database.transaction.FastStoreModelTransaction;
import com.raizlabs.android.dbflow.structure.database.transaction.ITransaction;
import com.raizlabs.android.dbflow.structure.database.transaction.QueryTransaction;
import com.raizlabs.android.dbflow.structure.database.transaction.Transaction;

import java.io.IOException;
import java.util.Date;
import java.util.List;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

public class NoteServiceImpl implements NoteService {

    private final Context context;
    private static NoteService instance;
    private final String baseUrl;

    private NoteServiceImpl(Context context, String baseUrl) {
        super();
        this.context = context;
        this.baseUrl = baseUrl;
    }

    public static NoteService getInstance(Context context, String baseUrl) {
        if (instance == null) {
            synchronized (NoteServiceImpl.class) {
                if (instance == null) {
                    instance = new NoteServiceImpl(context, baseUrl);
                }
            }
        }
        return instance;
    }

    @Override
    public void createUser(User user, final NoteCallback<User> callback) {
        Call<User> call = NoteServiceGenerator.createUserCall(user, baseUrl);
        call.enqueue(new Callback<User>() {
            @Override
            public void onResponse(Call<User> call, retrofit2.Response<User> response) {
                // Check if we get a 201 CREATED response back.
                if (response.code() == 201) {
                    // Call the  user passed callback.
                    callback.onResponse(response.body());
                }
                else {
                    callback.onError(response.code(), "There was an error returned from the server.");
                }
            }

            @Override
            public void onFailure(Call<User> call, Throwable t) {
                callback.onError(500, t.getMessage());
            }
        });
    }

    @Override
    public void login(String email, String password, final NoteCallback<String> callback) {
        Call<ResponseBody> call = NoteServiceGenerator.loginCall(email, password, baseUrl);
        call.enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, retrofit2.Response<ResponseBody> response) {
                // Check if we get a 200 OK response back.
                if (response.code() == 200) {
                    // Call the  user passed callback.
                    try {
                        callback.onResponse(response.body().string());
                    } catch (IOException e) {
                        callback.onError(500, "Error parsing string");
                    }
                }
                else {
                    callback.onError(response.code(), "There was an error returned from the server.");
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                Log.e("ERROR", t.getMessage());
                callback.onError(500, t.getMessage());
            }
        });

    }

    @Override
    public void createNote(final Note note, final String authToken, final NoteCallback<Note> callback) {
        NoteJobManager.getJobManager(context).addJobInBackground(new CreateNoteJob(note, authToken, baseUrl), new AsyncAddCallback() {
            @Override
            public void onAdded(long jobId) {
                callback.onResponse(note);
            }
        });

        NoteJobManager.getJobManager(context).clear();
    }

    @Override
    public void getNotes(User user, String authToken, final NoteCallback<List<Note>> callback) {
        Call<List<Note>> call = NoteServiceGenerator.createGetNotesCall(authToken, user.getId(), baseUrl);
        // Run the get query and persist the response to the local database.
        call.enqueue(new Callback<List<Note>>() {
            @Override
            public void onResponse(Call<List<Note>> call, final retrofit2.Response<List<Note>> response) {
                if (response.code() == 200) {
                    // Save all the returned Notes in the database as an async transaction
                    FastStoreModelTransaction<Note> fsmt = FastStoreModelTransaction
                            .insertBuilder(FlowManager.getModelAdapter(Note.class))
                            .addAll(response.body())
                            .build();
                    DatabaseDefinition database = FlowManager.getDatabase(NoteDB.class);
                    Transaction transaction = database.beginTransactionAsync(fsmt)
                            .success(new Transaction.Success() {
                                @Override
                                public void onSuccess(Transaction transaction) {
                                    // This runs on UI thread
                                    callback.onResponse(response.body());
                                }
                            }).error(new Transaction.Error() {
                                @Override
                                public void onError(Transaction transaction, Throwable error) {
                                    Log.e("ServiceError", error.getMessage());
                                    callback.onError(500, "Could not save to database");
                                }
                            }).build();
                    transaction.execute();
                }
            }

            @Override
            public void onFailure(Call<List<Note>> call, Throwable t) {
                callback.onError(500, t.getMessage());
            }
        });
    }

    @Override
    public void logout(User user, String authToken, final NoteCallback<String> callback) {
        NoteJobManager.getJobManager(context).addJobInBackground(new LogoutJob(user.getId(), authToken, baseUrl), new AsyncAddCallback() {
            @Override
            public void onAdded(long jobId) {
                callback.onResponse("Ok");
            }
        });
    }

    @Override
    public void loadNotes(final NoteCallback<List<Note>> callback) {
        SQLite.select().from(Note.class).async()
                .queryResultCallback(new QueryTransaction.QueryResultCallback<Note>() {
                    @Override
                    public void onQueryResult(QueryTransaction transaction, @NonNull CursorResult<Note> tResult) {
                        callback.onResponse(tResult.toList());
                    }
                }).execute();
    }
}

class NoteServiceGenerator {
    private static final HttpLoggingInterceptor interceptor = new HttpLoggingInterceptor();
    private static final OkHttpClient.Builder httpClient = new OkHttpClient.Builder();
    private static final Gson gson = new GsonBuilder()
            .registerTypeAdapter(Date.class, new GsonDateAdapter())
            .create();

    private static final OkHttpClient client = httpClient.addInterceptor(interceptor).build();

    private interface RetrofitNoteService {
        @POST("/user")
        Call<User> createUser(@Body User user);
        @POST("/auth")
        Call<ResponseBody> login();
        @POST("/user/{userId}/note")
        Call<Note> createNote(@Body  Note note, @Path("userId") String userId, @Query("authToken") String authToken);
        @GET("/user/{userId}/notes")
        Call<List<Note>> getNotes(@Path("userId") String userId, @Query("authToken") String authToken);
        @DELETE("/auth")
        Call<String> logout(@Query("userId")  String userId, @Query("authToken") String authToken);
    }

    private static RetrofitNoteService getBasicAuthService(String email, String password, String baseUrl) {
        if (email != null && password != null) {
            String credentials = email + ":" + password;
            final String basic =
                    "Basic " + Base64.encodeToString(credentials.getBytes(), Base64.NO_WRAP);

            httpClient.addInterceptor(new Interceptor() {
                @Override
                public Response intercept(Interceptor.Chain chain) throws IOException {
                    Request original = chain.request();

                    Request.Builder requestBuilder = original.newBuilder()
                            .header("Authorization", basic)
                            .header("Accept", "text/plain")
                            .method(original.method(), original.body());

                    Request request = requestBuilder.build();
                    return chain.proceed(request);
                }
            });

        }
        Retrofit.Builder builder =
            new Retrofit.Builder()
                    .baseUrl(baseUrl)
                    .addConverterFactory(GsonConverterFactory.create(gson));
        OkHttpClient client = httpClient.build();
        Retrofit retrofit = builder.client(client).build();
        return retrofit.create(RetrofitNoteService.class);

    }

    private static RetrofitNoteService getService(String baseUrl) {
        Retrofit.Builder builder =
                new Retrofit.Builder()
                        .baseUrl(baseUrl)
                        .addConverterFactory(GsonConverterFactory.create(gson));
        Retrofit retrofit = builder.client(client).build();
        return retrofit.create(RetrofitNoteService.class);
    }


    static Call<Note> createNoteCall(Note note, String authToken, String userId, String baseUrl) {
        return getService(baseUrl).createNote(note, userId, authToken);
    }

    static Call<ResponseBody> loginCall(String email, String password, String baseUrl) {
        return getBasicAuthService(email, password, baseUrl).login();
    }

    static Call<User> createUserCall(User user, String baseUrl) {
        return  getService(baseUrl).createUser(user);
    }

    static Call<List<Note>> createGetNotesCall(String authToken, String userId, String baseUrl) {
        return getService(baseUrl).getNotes(authToken, userId);
    }

    static Call<String> logout(String userId, String authToken, String baseUrl) {
        return getService(baseUrl).logout(userId, authToken);
    }
}


class CreateNoteJob extends Job {

    private final Note note;
    private final String authToken;
    private final String baseUrl;

    public CreateNoteJob(Note note, String authToken, String baseUrl) {
        super(new Params(1).requireNetwork().persist().groupBy("NOTE"));
        this.note = note;
        this.authToken = authToken;
        this.baseUrl = baseUrl;
    }

    @Override
    public void onAdded() {
        // Persist Note in database.
        note.save();
    }

    @Override
    public void onRun() throws Throwable {
        // Run the network call to post Note.
        // This can be just an execute call instead of an enqueue call since this is already backgrounded.
        Call<Note> call = NoteServiceGenerator.createNoteCall(note, note.getUserId(), authToken, baseUrl);
        call.execute();
    }

    @Override
    protected void onCancel() {

    }
}

class LogoutJob extends Job {

    private String userId;
    private String authToken;
    private String baseUrl;

    public LogoutJob(String userId, String authToken, String baseUrl) {
        super(new Params(1).requireNetwork().persist().groupBy("LOGOUT"));
        this.userId = userId;
        this.authToken = authToken;
    }

    @Override
    public void onAdded() {
        ITransaction it = new ITransaction() {
            @Override
            public void execute(DatabaseWrapper databaseWrapper) {
                // List all model tables here to delete.
                Delete.tables(Note.class);
            }
        };
        FlowManager.getDatabase(NoteDB.class).executeTransaction(it);
    }

    @Override
    public void onRun() throws Throwable {
        Call<String> call = NoteServiceGenerator.logout(userId, authToken, baseUrl);

        call.execute();
    }

    @Override
    protected void onCancel() {

    }
}

