package yzy.zyuanyuz.caldavclient4j.client.util;

import com.github.caldav4j.CalDAVConstants;
import com.github.caldav4j.exceptions.CalDAV4JException;
import com.github.caldav4j.methods.CalDAV4JMethodFactory;
import com.github.caldav4j.methods.HttpCalDAVReportMethod;
import com.github.caldav4j.methods.HttpPropFindMethod;
import com.github.caldav4j.model.request.CalendarQuery;
import com.github.caldav4j.model.request.CompFilter;
import com.github.caldav4j.util.CalDAVStatus;
import net.fortuna.ical4j.model.Calendar;
import net.fortuna.ical4j.model.Component;
import net.fortuna.ical4j.model.component.VEvent;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.TrustAllStrategy;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContextBuilder;
import org.apache.http.util.EntityUtils;
import org.apache.jackrabbit.webdav.DavException;
import org.apache.jackrabbit.webdav.MultiStatusResponse;
import org.apache.jackrabbit.webdav.property.DavPropertyName;
import org.apache.jackrabbit.webdav.property.DavPropertyNameSet;
import org.w3c.dom.Document;
import yzy.zyuanyuz.caldavclient4j.client.commons.ResourceEntry;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.apache.jackrabbit.webdav.property.DavPropertyName.DISPLAYNAME;
import static yzy.zyuanyuz.caldavclient4j.client.commons.ICloudCalDAVConstants.CURRENT_USER_PRINCIPAL_STR;
import static yzy.zyuanyuz.caldavclient4j.client.commons.ICloudCalDAVConstants.ICLOUD_CALDAV_URI;

/**
 * @author George Yu
 * @since 2019/10/9 10:43
 */
public final class ICloudCalDAVUtil {

  private ICloudCalDAVUtil() {}

  public static String getPrincipalId(HttpClient httpClient, CalDAV4JMethodFactory methodFactory)
      throws Exception {
    DavPropertyNameSet nameSet = new DavPropertyNameSet();
    nameSet.add(DavPropertyName.create(CURRENT_USER_PRINCIPAL_STR));
    HttpPropFindMethod propFindMethod =
        methodFactory.createPropFindMethod(ICLOUD_CALDAV_URI, nameSet, 0);
    HttpResponse response = httpClient.execute(propFindMethod);
    Document doc = propFindMethod.getResponseBodyAsDocument(response.getEntity());
    String href = doc.getElementsByTagName("href").item(1).getFirstChild().getNodeValue();
    return href.substring(1, href.indexOf("/principal/"));
  }

  /**
   * @param appleId
   * @param password
   * @return
   */
  public static HttpClient createHttpClieknt(String appleId, String password)
      throws CalDAV4JException {
    HttpHost target = new HttpHost("caldav.icloud.com", 443, "https");
    CredentialsProvider provider = new BasicCredentialsProvider();
    provider.setCredentials(
        new AuthScope(target.getHostName(), target.getPort()),
        new UsernamePasswordCredentials(appleId, password));
    try {
      return HttpClients.custom()
          .setSSLContext(
              new SSLContextBuilder().loadTrustMaterial(null, TrustAllStrategy.INSTANCE).build())
          .setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE)
          .setDefaultCredentialsProvider(provider)
          .build();
    } catch (Exception e) {
      throw new CalDAV4JException("Build http client failed!");
    }
  }

  /**
   * get all custom resource from server, so some default resource was excluded.
   *
   * @param httpClient the http client with user auth
   * @param principalId
   * @return
   */
  public static List<ResourceEntry> getAllResourceFromServer(
      HttpClient httpClient, CalDAV4JMethodFactory methodFactory, String principalId)
      throws IOException, DavException {
    String url = ICLOUD_CALDAV_URI + principalId + "/calendars";
    DavPropertyNameSet propertyNameSet = new DavPropertyNameSet();
    propertyNameSet.add(DISPLAYNAME);
    HttpPropFindMethod propFindMethod =
        methodFactory.createPropFindMethod(url, new DavPropertyNameSet(), 0);
    MultiStatusResponse[] multiStatusResponse =
        propFindMethod
            .getResponseBodyAsMultiStatus(httpClient.execute(propFindMethod))
            .getResponses();
    for (MultiStatusResponse res : multiStatusResponse) {
      if (res.getStatus()[0].getStatusCode() == CalDAVStatus.SC_OK) {
        Arrays.stream(res.getStatus()).forEach(System.out::println);
      }
    }
    return null;
  }

  /**
   * @param calendarFolder
   * @param httpClient
   * @param methodFactory
   * @return
   * @throws Exception
   */
  public static List<String> getEventUidList(
      String calendarFolder, HttpClient httpClient, CalDAV4JMethodFactory methodFactory)
      throws Exception {
    String userId = getPrincipalId(httpClient, methodFactory); // e.g. 16884482682
    String url = ICLOUD_CALDAV_URI + userId + "/calendars/" + calendarFolder;

    DavPropertyNameSet properties = new DavPropertyNameSet();
    properties.add(DavPropertyName.GETETAG);

    CompFilter filter = new CompFilter(Calendar.VCALENDAR);
    filter.addCompFilter(new CompFilter(Component.VEVENT));

    CalendarQuery query = new CalendarQuery(properties, filter, null, false, false);
    HttpCalDAVReportMethod reportMethod =
        methodFactory.createCalDAVReportMethod(url, query, CalDAVConstants.DEPTH_1);
    HttpResponse response = httpClient.execute(reportMethod);
    System.out.println(EntityUtils.toString(response.getEntity()));
    return null;
    //    MultiStatus multiStatus = reportMethod.getResponseBodyAsMultiStatus(response);
    //    MultiStatusResponse[] multiStatusResponses = multiStatus.getResponses();
    //    return Arrays.stream(multiStatusResponses)
    //        .skip(1)
    //        .map(MultiStatusResponse::getHref)
    //        .map(href -> href.substring(href.indexOf(calendarFolder) + calendarFolder.length()))
    //        .collect(Collectors.toList());
  }

  public static String pathToCalendar(String principal, String calFolder, String uuid) {
    return ICLOUD_CALDAV_URI + "/" + principal + "/calendars/" + calFolder + "/" + uuid + ".ics";
  }

  public static String getUidFromHref(String href) {
    return href.substring(href.lastIndexOf("/") + 1, href.indexOf(".ics"));
  }

  public static List<VEvent> getEventsFromCalendars(List<Calendar> calendars) {
    return calendars.stream()
        .flatMap(c -> c.getComponents(Component.VEVENT).stream())
        .map(e -> (VEvent) e)
        .collect(Collectors.toList());
  }
}
