package ex.train;

/** General-purpose simple array-based implementation of AbstractPhrase. */
public class GenericPhrase implements AbstractPhrase {
    AbstractToken[] tokens;
    int len=0;
    Object data;

    public GenericPhrase(int maxLen) {
        tokens=new AbstractToken[maxLen];
    }
    public GenericPhrase(AbstractPhrase phrase) {
        tokens=new AbstractToken[phrase.getLength()];
        for(int i=0;i<tokens.length;i++) {
            tokens[i]=phrase.getToken(i);
        }
        len=tokens.length;
        data=phrase.getData();
    }
    public void ensureCapacity(int cap) {
        if(tokens.length<cap) {
            AbstractToken[] old=tokens;
            tokens=new TokenInfo[cap];
            System.arraycopy(old, 0, tokens, 0, old.length);
        }
    }
    public int getLength() {
        return len;
    }
    public AbstractToken getToken(int idx) {
        return tokens[idx];
    }
    public Object getData() {
        return data;
    }
    public StringBuffer toString(StringBuffer b) {
        for(int i=0;i<len;i++) {
            if(i>0)
                b.append(' ');
            b.append(tokens[i].getToken());
        }
        return b;
    }
    public String toString() {
        return toString(new StringBuffer(64)).toString();
    }
}
