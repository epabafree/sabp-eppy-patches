package app.morphe.extension.sabp.ui;

import android.app.Activity;
import android.view.ViewGroup;

import app.morphe.extension.sabp.EppyPlayer;
import app.morphe.extension.sabp.EppyTheme;

/** What the screens need from the activity hosting them. */
public interface Host {
    Activity activity();

    EppyTheme theme();

    EppyPlayer player();

    /** Root view that bottom sheets attach to. */
    ViewGroup overlayRoot();

    void openPlayer();

    void closePlayer();

    void openSabp(String activityClass);

    void showMoreSheet();

    void selectTab(int tab);

    void rebuild();

    void playBook(EppyPlayer.Book book, boolean openPlayer);

    /** 0 = toggle a bookmark at this moment, 1 = toggle one at the start of this chapter. */
    void bookmarkAction(int kind);

    /** Shows a short message with an Undo action. */
    void showUndo(String message, Runnable undo);

    /** Opens the system picker for an EPUB/PDF to pair with the playing book. */
    void pickDocument();

    /** Explains and requests the permission Android's speech service needs, then runs action. */
    void ensureSpeechPermission(Runnable action);
}
