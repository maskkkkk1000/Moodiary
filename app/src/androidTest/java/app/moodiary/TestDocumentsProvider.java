package app.moodiary;

import android.database.Cursor;
import android.database.MatrixCursor;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract.Document;
import android.provider.DocumentsProvider;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

/** Instrumentation-only real filesystem SAF provider; absent from the delivered application. */
public class TestDocumentsProvider extends DocumentsProvider {
    private File root() {
        File value = new File(getContext().getCacheDir(), "saf-test-files"); value.mkdirs();
        try { return value.getCanonicalFile(); } catch (IOException e) { throw new IllegalStateException(e); }
    }
    private File file(String id) {
        try {
            File root = root().getCanonicalFile();
            if (id.equals("root")) return root;
            File value = new File(root, id).getCanonicalFile();
            if (!value.getPath().startsWith(root.getPath() + File.separator)) throw new IllegalArgumentException();
            return value;
        } catch (IOException e) { throw new IllegalArgumentException(e); }
    }
    private String id(File value) { return value.getPath().substring(root().getPath().length() + 1); }
    private static final String[] COLUMNS = {Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME,
        Document.COLUMN_MIME_TYPE, Document.COLUMN_FLAGS, Document.COLUMN_SIZE, Document.COLUMN_LAST_MODIFIED};
    private void row(MatrixCursor cursor, File value) {
        Object[] values = new Object[cursor.getColumnCount()]; String[] names = cursor.getColumnNames();
        for (int i = 0; i < names.length; i++) {
            switch (names[i]) {
                case Document.COLUMN_DOCUMENT_ID: values[i] = value.equals(root()) ? "root" : id(value); break;
                case Document.COLUMN_DISPLAY_NAME: values[i] = value.getName(); break;
                case Document.COLUMN_MIME_TYPE: values[i] = value.isDirectory() ? Document.MIME_TYPE_DIR : "application/zip"; break;
                case Document.COLUMN_FLAGS: values[i] = Document.FLAG_SUPPORTS_DELETE | Document.FLAG_SUPPORTS_RENAME |
                    (value.isDirectory() ? Document.FLAG_DIR_SUPPORTS_CREATE : Document.FLAG_SUPPORTS_WRITE); break;
                case Document.COLUMN_SIZE: values[i] = value.length(); break;
                case Document.COLUMN_LAST_MODIFIED: values[i] = value.lastModified(); break;
            }
        }
        cursor.addRow(values);
    }
    @Override public boolean onCreate() { return true; }
    @Override public Cursor queryRoots(String[] projection) { return new MatrixCursor(projection == null ? new String[]{"root_id"} : projection); }
    @Override public Cursor queryDocument(String documentId, String[] projection) {
        MatrixCursor result = new MatrixCursor(projection == null ? COLUMNS : projection); row(result, file(documentId)); return result;
    }
    @Override public Cursor queryChildDocuments(String parentId, String[] projection, String order) {
        MatrixCursor result = new MatrixCursor(projection == null ? COLUMNS : projection);
        File[] children = file(parentId).listFiles(); if (children != null) for (File child : children) row(result, child);
        return result;
    }
    @Override public ParcelFileDescriptor openDocument(String documentId, String mode, CancellationSignal signal) throws FileNotFoundException {
        return ParcelFileDescriptor.open(file(documentId), ParcelFileDescriptor.parseMode(mode));
    }
    private void validName(String name) { if (name.contains("/") || name.contains("\\") || name.equals("..")) throw new IllegalArgumentException(); }
    @Override public String createDocument(String parentId, String mimeType, String name) throws FileNotFoundException {
        validName(name); File child = new File(file(parentId), name);
        try { if (!(mimeType.equals(Document.MIME_TYPE_DIR) ? child.mkdir() : child.createNewFile())) throw new FileNotFoundException(); }
        catch (IOException error) { throw new FileNotFoundException(error.toString()); }
        return id(child);
    }
    @Override public String renameDocument(String documentId, String name) throws FileNotFoundException {
        validName(name); File source = file(documentId); File destination = new File(source.getParentFile(), name);
        if (!source.renameTo(destination)) throw new FileNotFoundException(); return id(destination);
    }
    private void remove(File value) { File[] children = value.listFiles(); if (children != null) for (File child : children) remove(child); if (!value.delete()) throw new IllegalStateException(); }
    @Override public void deleteDocument(String documentId) { if (documentId.equals("root")) throw new IllegalArgumentException(); remove(file(documentId)); }
    @Override public boolean isChildDocument(String parentId, String documentId) { return file(documentId).getPath().startsWith(file(parentId).getPath() + File.separator); }
}
