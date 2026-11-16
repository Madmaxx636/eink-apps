package app.eink.notes;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import app.eink.notes.format.Doc;

/** How a document is kept on disk inside the app: small, readable JSON. */
final class DocJson {
    private DocJson() {}

    static String write(Doc d) {
        try {
            JSONArray paras = new JSONArray();
            for (Doc.Para p : d.paras) {
                JSONObject o = new JSONObject();
                if (p.heading > 0) o.put("h", p.heading);
                if (p.list != null) o.put("l", p.list);
                if (p.align != 0) o.put("a", p.align);
                JSONArray runs = new JSONArray();
                for (Doc.Run r : p.runs) {
                    JSONObject j = new JSONObject().put("t", r.text);
                    if (r.bold) j.put("b", 1);
                    if (r.italic) j.put("i", 1);
                    if (r.underline) j.put("u", 1);
                    if (r.strike) j.put("s", 1);
                    if (r.size != 1f) j.put("z", (double) r.size);
                    if (r.family != null) j.put("f", r.family);
                    runs.put(j);
                }
                o.put("r", runs);
                paras.put(o);
            }
            return new JSONObject().put("v", 1).put("p", paras).toString();
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    static Doc read(String json) throws JSONException {
        Doc d = new Doc();
        JSONArray paras = new JSONObject(json).getJSONArray("p");
        for (int i = 0; i < paras.length(); i++) {
            JSONObject o = paras.getJSONObject(i);
            Doc.Para p = d.newPara();
            p.heading = o.optInt("h", 0);
            p.list = o.has("l") ? o.getString("l") : null;
            p.align = o.optInt("a", 0);
            JSONArray runs = o.optJSONArray("r");
            if (runs == null) continue;
            for (int k = 0; k < runs.length(); k++) {
                JSONObject j = runs.getJSONObject(k);
                Doc.Run r = new Doc.Run(j.getString("t"));
                r.bold = j.optInt("b") == 1;
                r.italic = j.optInt("i") == 1;
                r.underline = j.optInt("u") == 1;
                r.strike = j.optInt("s") == 1;
                r.size = (float) j.optDouble("z", 1.0);
                r.family = j.has("f") ? j.getString("f") : null;
                p.add(r);
            }
        }
        if (d.paras.isEmpty()) d.newPara();
        return d;
    }
}
