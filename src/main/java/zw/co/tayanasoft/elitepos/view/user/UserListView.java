package zw.co.tayanasoft.elitepos.view.user;

import zw.co.tayanasoft.elitepos.entity.User;
import zw.co.tayanasoft.elitepos.view.main.MainView;
import com.vaadin.flow.router.Route;
import io.jmix.flowui.view.*;

@Route(value = "users", layout = MainView.class)
@ViewController(id = "User.list")
@ViewDescriptor(path = "user-list-view.xml")
@LookupComponent("usersDataGrid")
@DialogMode(width = "64em")
public class UserListView extends StandardListView<User> {
}
