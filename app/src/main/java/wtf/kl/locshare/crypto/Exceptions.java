package wtf.kl.locshare.crypto;

class InvalidMessage extends Exception {
    static final long serialVersionUID = 0;
    public InvalidMessage() { super(); }
    public InvalidMessage(String message) { super(message); }
    public InvalidMessage(String message, Throwable cause) { super(message, cause); }
    public InvalidMessage(Throwable cause) { super(cause); }
}

class InvalidParameters extends Exception {
    static final long serialVersionUID = 0;
    public InvalidParameters() { super(); }
    public InvalidParameters(String message) { super(message); }
    public InvalidParameters(String message, Throwable cause) { super(message, cause); }
    public InvalidParameters(Throwable cause) { super(cause); }
}
