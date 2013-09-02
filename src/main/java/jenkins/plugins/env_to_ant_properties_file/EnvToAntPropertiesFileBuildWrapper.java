package jenkins.plugins.env_to_ant_properties_file;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.model.Hudson;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;

import org.kohsuke.stapler.DataBoundConstructor;

import java.io.*;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class EnvToAntPropertiesFileBuildWrapper extends BuildWrapper {

    private File propertiesFile;

    @DataBoundConstructor
    public EnvToAntPropertiesFileBuildWrapper(File propertiesFile) {
        this.propertiesFile = propertiesFile;
    }

    @Extension
    public static class DescriptorImpl extends BuildWrapperDescriptor {

        @Override
        public String getDisplayName() {
            return "Environment to Ant Properties File";
        }

        @Override
        public boolean isApplicable(AbstractProject<?, ?> arg0) {
            return true;
        }
    }

    @SuppressWarnings({"rawtypes"})
    public Environment setUp(final AbstractBuild build, final Launcher launcher, final BuildListener listener)
            throws IOException, InterruptedException {
        //getBuildVariables leaves out system variables, including the workspace
        final Map envVars = build.getBuildVariables();
        try {
            envVars.put("JOB_NAME", build.getEnvironment().get("JOB_NAME"));
            envVars.put("P4_CHANGELIST", build.getEnvironment().get("P4_CHANGELIST"));
        } catch (Exception e) {
            //Variable did not exist
        }

        listener.getLogger().print("Writing environment variables to properties file.  ");

        FilePath nodePath = new FilePath(build.getWorkspace(), propertiesFile.getPath());

        String jenkinsRoot = Hudson.getInstance().getRootUrl();

        if (jenkinsRoot == null) {
            jenkinsRoot = "http://localhost:8080/";
        }

        String propertiesFileURL = jenkinsRoot
                + build.getParent().getUrl()
                + "ws/" + URLParamEncoder.encode(propertiesFile.getPath().replaceAll("\\\\", "/"))
                + "/*view*";
        //Print out the path to the file
        listener.hyperlink(propertiesFileURL, propertiesFileURL);
        listener.getLogger().println("");

        //Create the file and write the current variables to it
        PrintWriter writer = new PrintWriter(nodePath.write());
        Iterator it = envVars.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry entry = (Map.Entry) it.next();
            writer.println(entry.getKey() + "=" + entry.getValue());
        }
        // Close the file
        writer.close();

        return new BuildWrapper.Environment() {
        };
    }

    private String replaceEnvVars(Map map, String str) {
        //search for variables of the from ${VARNAME}
        Pattern pattern = Pattern.compile("\\$\\{.+?\\}");
        Matcher m = pattern.matcher(str);
        while (m.find()) {
            String s = m.group();
            //strip the variable indicator
            String varName = s.replaceAll("[${}]", "");
            //find the value of the variable
            String varVal = (String) map.get(varName);
            //if variable still has the form ${}, dereference it again (so long as it doesn't reference itself)
            if (s != varVal && varVal.matches("\\$\\{.+?\\}")) {
                varVal = replaceEnvVars(map, varVal);
            }

            //replace${var} with the expanded environment variable
            if (varVal != null) {
                str = str.replace(s, varVal);
            }
        }
        return str;
    }

    public File getPropertiesFile() {
        return propertiesFile;
    }

    public void setPropertiesFile(File propertiesFile) {
        this.propertiesFile = propertiesFile;
    }

    public static class URLParamEncoder {

        public static String encode(String input) {
            StringBuilder resultStr = new StringBuilder();
            for (char ch : input.toCharArray()) {
                if (isUnsafe(ch)) {
                    resultStr.append('%');
                    resultStr.append(toHex(ch / 16));
                    resultStr.append(toHex(ch % 16));
                } else {
                    resultStr.append(ch);
                }
            }
            return resultStr.toString();
        }

        private static char toHex(int ch) {
            return (char) (ch < 10 ? '0' + ch : 'A' + ch - 10);
        }

        private static boolean isUnsafe(char ch) {
            if (ch > 128 || ch < 0) {
                return true;
            }
            return " %$&+,/:;=?@<>#%".indexOf(ch) >= 0;
        }
    }
}
