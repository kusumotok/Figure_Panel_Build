package org.microscopy.panel;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Reads and writes the document as JSON. The same text is what gets embedded in the PPTX project
 * part, so this is the one definition of the on-disk schema.
 */
public final class DocumentSerializer {
  private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
  private final Gson compact = new Gson();

  public String toJson(Document document) {
    document.validate();
    return gson.toJson(document);
  }

  public Document fromJson(String json) {
    Document document;
    try {
      document = compact.fromJson(json, Document.class);
    } catch (JsonSyntaxException ex) {
      throw new IllegalArgumentException("Project data is not valid JSON.", ex);
    }
    if (document == null) throw new IllegalArgumentException("Project data is empty.");
    try {
      document.validate();
    } catch (NullPointerException ex) {
      throw new IllegalArgumentException("Project data is missing required fields.", ex);
    }
    return document;
  }

  /** Deep copy through the serialised form, matching how the figure history takes snapshots. */
  public Document copy(Document document) {
    return compact.fromJson(compact.toJson(document), Document.class);
  }

  public void save(File file, Document document) throws IOException {
    ProjectSafety.checkDestination(file, document);
    String json = toJson(document);
    Path destination = file.toPath().toAbsolutePath();
    Path temp = Files.createTempFile(destination.getParent(), "panel-project-", ".tmp");
    try {
      try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
        writer.write(json);
      }
      Files.move(temp, destination, StandardCopyOption.REPLACE_EXISTING);
    } finally {
      Files.deleteIfExists(temp);
    }
  }

  public Document load(File file) throws IOException {
    try (Reader reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
      StringBuilder text = new StringBuilder();
      char[] buffer = new char[8192];
      int read;
      while ((read = reader.read(buffer)) != -1) text.append(buffer, 0, read);
      return fromJson(text.toString());
    }
  }

  /** Compact JSON for any model object, used to key caches on exact content. */
  public String toJsonFragment(Object value) { return compact.toJson(value); }
}
