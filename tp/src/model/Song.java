package model;

import interfaces.Recordable;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;

public class Song implements Recordable {

    public static final int MODE_LENGTH = 5;

    private final int id;
    private final String trackName;
    private final String[] artistsName;
    private final LocalDate releasedDate;
    private final int inSpotifyPlaylists;
    private final long streams;
    private final int bpm;
    private final char[] mode;

    public Song(
        int id,
        String trackName,
        String[] artistsName,
        int releasedYear,
        int releasedMonth,
        int releasedDay,
        int inSpotifyPlaylists,
        long streams,
        int bpm,
        String mode) {
        this(id, trackName, artistsName, LocalDate.of(releasedYear, releasedMonth, releasedDay),
                inSpotifyPlaylists, streams, bpm, mode);
    }

    private Song(
        int id,
        String trackName,
        String[] artistsName,
        LocalDate releasedDate,
        int inSpotifyPlaylists,
        long streams,
        int bpm,
        String mode) {

        if (id < 0) {
            throw new IllegalArgumentException("Id precisa ser sem sinal (>= 0)");
        }
        this.id = id;
        this.trackName = trackName;
        this.artistsName = artistsName;
        this.releasedDate = releasedDate;
        this.inSpotifyPlaylists = inSpotifyPlaylists;
        this.streams = streams;
        this.bpm = bpm;
        this.mode = String.format("%-" + MODE_LENGTH + "s", mode).substring(0, MODE_LENGTH).toCharArray();
    }

    @Override
    public int id() {
        return id;
    }

    @Override
    public byte[] toBytes() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(id);
            out.writeUTF(trackName);

            out.writeInt(artistsName.length);
            for (String artist : artistsName) {
                out.writeUTF(artist);
            }

            out.writeLong(releasedDate.toEpochDay());
            out.writeInt(inSpotifyPlaylists);
            out.writeLong(streams);
            out.writeInt(bpm);
            out.writeChars(new String(mode));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return bytes.toByteArray();
    }

    public static Song fromBytes(byte[] data) {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
            int id = in.readInt();
            String trackName = in.readUTF();

            String[] artistsName = new String[in.readInt()];
            for (int i = 0; i < artistsName.length; i++) {
                artistsName[i] = in.readUTF();
            }

            LocalDate releasedDate = LocalDate.ofEpochDay(in.readLong());
            int inSpotifyPlaylists = in.readInt();
            long streams = in.readLong();
            int bpm = in.readInt();

            char[] modeChars = new char[MODE_LENGTH];
            for (int i = 0; i < MODE_LENGTH; i++) {
                modeChars[i] = in.readChar();
            }
            
            return new Song(id, trackName, artistsName, releasedDate,
                    inSpotifyPlaylists, streams, bpm, new String(modeChars));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public String toString() {
        return "Song{" +
                "id=" + id +
                ", trackName='" + trackName + '\'' +
                ", artistsName=" + String.join(", ", artistsName) +
                ", releasedDate=" + releasedDate +
                ", inSpotifyPlaylists=" + inSpotifyPlaylists +
                ", streams=" + streams +
                ", bpm=" + bpm +
                ", mode='" + new String(mode).trim() + '\'' +
                '}';
    }
}
