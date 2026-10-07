// Owner: Nguoi3

package exam.client.monitor;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

/** Resolve domain bằng DNS của hệ điều hành. */
public class SystemDomainResolver implements DomainResolver {

    @Override
    public List<String> resolve(String domain) {
        List<String> addresses = new ArrayList<>();
        try {
            for (InetAddress address : InetAddress.getAllByName(domain)) {
                addresses.add(address.getHostAddress());
            }
        } catch (UnknownHostException e) {
            System.out.println("[Rule] Không resolve được " + domain + ": " + e.getMessage());
        }
        return addresses;
    }
}
