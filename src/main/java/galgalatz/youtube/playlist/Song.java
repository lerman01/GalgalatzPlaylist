package galgalatz.youtube.playlist;

public class Song {
	private String title;
	private String autor;

	public Song(String title, String autor) {
		this.title = title;
		this.autor = autor;
	}

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getAutor() {
		return autor;
	}

	public void setAutor(String autor) {
		this.autor = autor;
	}

	@Override
	public String toString() {
		return "Song [title=" + title + ", autor=" + autor + "]";
	}

}
