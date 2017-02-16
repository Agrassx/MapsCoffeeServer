package model.frontresponse;

import model.User;

public class ResponseCafeInfo {

    private String id;
    private boolean ok;
    private float rating;
    private User added_by;
    private boolean found;

    public ResponseCafeInfo(String id, float rating, User added_by) {
        this.id = id;
        this.ok = true;
        if (added_by != null) {
            this.added_by = added_by;
            this.rating = rating;
            this.found = true;
        } else {
            this.found = false;
        }
    }
}
