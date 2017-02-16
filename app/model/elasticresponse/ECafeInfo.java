package model.elasticresponse;

import model.User;

public class ECafeInfo {

    private String _id;
    private boolean found;
    private Source _source;


    public String getID() {
        return _id;
    }

    public boolean isFound() {
        return found;
    }

    public float getLastRating() {
        if (_source == null) {
            return 0;
        }
        return _source.getLastRating();
    }

    public User getAddedBy() {
        if (_source == null) {
            return null;
        }
        return _source.getAddedBy();
    }
}

class Source {
    private float last_rating;
    private User added_by;

    float getLastRating() {
        return last_rating;
    }

    User getAddedBy() {
        return added_by;
    }
}
