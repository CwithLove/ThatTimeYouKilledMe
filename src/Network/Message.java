package Network;

import java.io.*;

public class Message implements Serializable {
    public int clientId;
    public String contenu;

    public Message(int clientId, String contenu) {
        this.clientId = clientId;
        this.contenu = contenu;
    }
}

