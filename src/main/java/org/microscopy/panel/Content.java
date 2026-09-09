package org.microscopy.panel;

/**
 * Content is the only place where a node gains type-specific behaviour. Kept as a discriminated
 * record with nullable payloads so the whole document stays a plain Gson tree without needing a
 * polymorphic type adapter.
 */
public final class Content {
  public enum Kind { NONE, TEXT, SCIENTIFIC_IMAGE, IMAGE, SHAPE }

  public Kind kind = Kind.NONE;
  public TextContent text;
  public ScientificImageContent scientificImage;
  public ImageContent image;
  public ShapeContent shape;

  public static Content none() { return new Content(); }

  public static Content of(TextContent text) {
    Content content = new Content();
    content.kind = Kind.TEXT; content.text = text;
    return content;
  }

  public static Content of(ScientificImageContent image) {
    Content content = new Content();
    content.kind = Kind.SCIENTIFIC_IMAGE; content.scientificImage = image;
    return content;
  }

  public static Content of(ImageContent image) {
    Content content = new Content();
    content.kind = Kind.IMAGE; content.image = image;
    return content;
  }

  public static Content of(ShapeContent shape) {
    Content content = new Content();
    content.kind = Kind.SHAPE; content.shape = shape;
    return content;
  }

  public void validate(String where) {
    if (kind == null) throw new IllegalArgumentException(where + ": missing content kind.");
    int present = (text != null ? 1 : 0) + (scientificImage != null ? 1 : 0)
        + (image != null ? 1 : 0) + (shape != null ? 1 : 0);
    if (kind == Kind.NONE) {
      if (present != 0) throw new IllegalArgumentException(where + ": empty content carries a payload.");
      return;
    }
    if (present != 1) throw new IllegalArgumentException(where + ": content must carry exactly one payload.");
    switch (kind) {
      case TEXT:
        if (text == null) throw new IllegalArgumentException(where + ": text content payload missing.");
        text.validate(where);
        break;
      case SCIENTIFIC_IMAGE:
        if (scientificImage == null) throw new IllegalArgumentException(where + ": image payload missing.");
        scientificImage.validate(where);
        break;
      case IMAGE:
        if (image == null) throw new IllegalArgumentException(where + ": picture payload missing.");
        image.validate(where);
        break;
      default:
        if (shape == null) throw new IllegalArgumentException(where + ": shape payload missing.");
        shape.validate(where);
        break;
    }
  }
}
