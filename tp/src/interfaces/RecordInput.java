package interfaces;

public interface RecordInput<T extends Recordable> {

    int readMenuOption();

    int readId();

    T readRecord(int id);
}
