import org.processmining.contexts.cli.CLIContext;
import org.processmining.framework.plugin.PluginContext;

public class DummyContext extends CLIContext {
	public PluginContext getContext() {
		return getMainPluginContext();
	}
}
