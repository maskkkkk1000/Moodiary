package app.moodiary;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.DocumentsContract;

/** Framework-only helper: the separately launched test APK does not package app Kotlin classes. */
public class TestGrantActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        String id = getIntent().getStringExtra("documentId");
        if (id == null) id = "root";
        if (!id.equals("root") && !id.matches("run-[0-9a-f-]+")) throw new IllegalArgumentException();
        grantUriPermission("app.moodiary", DocumentsContract.buildTreeDocumentUri("app.moodiary.test.documents", id),
            Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION |
            Intent.FLAG_GRANT_PREFIX_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        finish();
    }
}
