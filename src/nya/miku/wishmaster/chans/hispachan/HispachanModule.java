/*
 * Overchan Android (Meta Imageboard Client)
 * Copyright (C) 2014-2016  miku-nyan <https://github.com/miku-nyan>
 *     
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package nya.miku.wishmaster.chans.hispachan;

import java.io.ByteArrayOutputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringEscapeUtils;

import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.support.v4.content.res.ResourcesCompat;
import cz.msebera.android.httpclient.Header;
import cz.msebera.android.httpclient.HttpHeaders;
import nya.miku.wishmaster.R;
import nya.miku.wishmaster.api.AbstractKusabaModule;
import nya.miku.wishmaster.api.interfaces.CancellableTask;
import nya.miku.wishmaster.api.interfaces.ProgressListener;
import nya.miku.wishmaster.api.models.BoardModel;
import nya.miku.wishmaster.api.models.DeletePostModel;
import nya.miku.wishmaster.api.models.SendPostModel;
import nya.miku.wishmaster.api.models.SimpleBoardModel;
import nya.miku.wishmaster.api.util.ChanModels;
import nya.miku.wishmaster.common.IOUtils;
import nya.miku.wishmaster.http.ExtendedMultipartBuilder;
import nya.miku.wishmaster.http.streamer.HttpRequestModel;
import nya.miku.wishmaster.http.streamer.HttpResponseModel;
import nya.miku.wishmaster.http.streamer.HttpStreamer;

@SuppressLint("SimpleDateFormat")
public class HispachanModule extends AbstractKusabaModule {
    private static final String CHAN_NAME = "hispachan";
    private static final String[] DOMAINS = { "www.hispachan.org", "hispachan.org" };
    private static final SimpleBoardModel[] BOARDS = new SimpleBoardModel[] {
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "g", "General", "General", true),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "a", "Anime y Manga", "Intereses", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "c", "Ciencia y Matemáticas ", "Intereses", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "co", "Cómics y Animación", "Intereses", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "di", "Dibujo y Arte", "Intereses", true),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "fun", "Funposting", "Intereses", true),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "hu", "Humanidades", "Intereses", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "mcs", "Música, Cine y Series", "Intereses", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "mlp", "My Little Pony", "Intereses", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "pol", "Política", "Intereses", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "t", "Tecnología y Programación", "Intereses", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "v", "Videojuegos", "Intereses", false),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "s", "Chicas Sexy", "Sexy", true),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "lgbt", "LGBT", "Sexy", true),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "h", "Hentai", "Sexy", true),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "arg", "Argentina", "Regional", true),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "cl", "Chile", "Regional", true),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "col", "Colombia", "Regional", true),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "esp", "España", "Regional", true),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "mex", "México", "Regional", true),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "pe", "Perú", "Regional", true),
            ChanModels.obtainSimpleBoardModel(CHAN_NAME, "ve", "Venezuela", "Regional", true)
    };
    private static final String[] ATTACHMENT_FORMATS = new String[] { "gif", "jpg", "png", "pdf", "swf", "webm" };
    private static final DateFormat DATE_FORMAT = new SimpleDateFormat("dd/MM/yy HH:mm");
    static {
        DATE_FORMAT.setTimeZone(TimeZone.getTimeZone("GMT"));
    }
    
    private static final Pattern EMAIL_OPTIONS = Pattern.compile("^(noko|dado|OP|fortuna)?(sage)?$");
    private static final Pattern ERROR_POSTING = Pattern.compile("<div class=\"diverror\"[^>]*>(?:.*<br[^>]*>)?(.*?)</div>", Pattern.DOTALL);
    
    public HispachanModule(SharedPreferences preferences, Resources resources) {
        super(preferences, resources);
    }
    
    @Override
    public String getChanName() {
        return CHAN_NAME;
    }
    
    @Override
    public String getDisplayingName() {
        return "Hispachan";
    }
    
    @Override
    public Drawable getChanFavicon() {
        return ResourcesCompat.getDrawable(resources, R.drawable.favicon_hispachan, null);
    }
    
    @Override
    protected String getUsingDomain() {
        return DOMAINS[0];
    }
    
    @Override
    protected String[] getAllDomains() {
        return DOMAINS;
    }
    
    @Override
    protected boolean canCloudflare() {
        return true;
    }
    
    @Override
    protected boolean canHttps() {
        return true;
    }
    
    @Override
    protected DateFormat getDateFormat() {
        return DATE_FORMAT;
    }
    
    @Override
    protected int getKusabaFlags() {
        return ~(KusabaReader.FLAG_HANDLE_EMBEDDED_POST_POSTPROCESS|KusabaReader.FLAG_OMITTED_STRING_REMOVE_HREF);
    }
    
    @Override
    protected SimpleBoardModel[] getBoardsList() {
        return BOARDS;
    }
    
    @Override
    public BoardModel getBoard(String shortName, ProgressListener listener, CancellableTask task) throws Exception {
        BoardModel model = super.getBoard(shortName, listener, task);
        model.defaultUserName = shortName.equals("fun") ? "Hanonymouz" : "Anónimo";
        model.allowDeletePosts = false;
        model.allowDeleteFiles = false;
        model.allowNames = false;
        model.ignoreEmailIfSage = false;
        model.allowCustomMark = true;
        model.customMarkDescription = "Spoiler";
        model.attachmentsFormatFilters = ATTACHMENT_FORMATS;
        model.markType = BoardModel.MARK_BBCODE;
        return model;
    }
    
    @Override
    public String sendPost(SendPostModel model, ProgressListener listener, CancellableTask task) throws Exception {
        String url = getUsingUrl() + "board.php";
        ExtendedMultipartBuilder postEntityBuilder = ExtendedMultipartBuilder.create().setDelegates(listener, task);
        Matcher matcher = EMAIL_OPTIONS.matcher(model.email);
        String emOptions;
        if (matcher.matches()) {
            emOptions = matcher.group(1) != null ? matcher.group(1) : "noko";
            if (model.sage || matcher.group(2) != null) emOptions += "sage";
        } else {
            emOptions = model.sage ? "nokosage" : "noko";
        }
        postEntityBuilder.
            addString("board", model.boardName).
            addString("replythread", model.threadNumber == null ? "0" : model.threadNumber).
            addString("name", model.name).
            addString("em", emOptions).
            addString("subject", model.subject).
            addString("message", model.comment);
        setSendPostEntityAttachments(model, postEntityBuilder);
        
        HttpRequestModel request = HttpRequestModel.builder().setPOST(postEntityBuilder.build()).setNoRedirect(true).build();
        HttpResponseModel response = null;
        try {
            response = HttpStreamer.getInstance().getFromUrl(url, request, httpClient, null, task);
            if (response.statusCode == 302) {
                if (!model.email.startsWith("noko")) return null;
                for (Header header : response.headers) {
                    if (header != null && HttpHeaders.LOCATION.equalsIgnoreCase(header.getName())) {
                        return fixRelativeUrl(header.getValue());
                    }
                }
            } else if (response.statusCode == 200) {
                ByteArrayOutputStream output = new ByteArrayOutputStream(1024);
                IOUtils.copyStream(response.stream, output);
                String htmlResponse = output.toString("UTF-8");
                if (htmlResponse.contains("<div class=\"divban\">")) {
                    //parse ban message
                    throw new Exception("¡ESTÁS BANEADO!");
                }
                Matcher errorMatcher = ERROR_POSTING.matcher(htmlResponse);
                if (errorMatcher.find()) {
                    throw new Exception(StringEscapeUtils.unescapeHtml4(errorMatcher.group(1).trim()));
                }
            } else {
                throw new Exception(response.statusCode + " - " + response.statusReason);
            }
        } finally {
            if (response != null) response.release();
        }
        return null;
    }
    
    @Override
    protected String getReportFormValue(DeletePostModel model) {
        return "Reportar";
    }
    
    //TODO: Implement Hispachan reader
    
}
