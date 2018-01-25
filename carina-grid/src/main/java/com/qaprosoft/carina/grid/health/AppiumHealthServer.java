package com.qaprosoft.carina.grid.health;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.PumpStreamHandler;
import org.apache.log4j.BasicConfigurator;
import org.seleniumhq.jetty9.server.Server;
import org.seleniumhq.jetty9.servlet.ServletHandler;

/**
 * AppiumHealthServer - allows to restart iOS Appium remotely.
 * 
 * @author Alex Khursevich (alex@qaprosoft.com)
 */
public class AppiumHealthServer
{	
	private static String HOST = "localhost";
	private static String PORT = "8020";
	private static String SCRIPTS = ClassLoader.getSystemResource("scripts").getPath();
	
    public static void main(String[] args) throws Exception
    {
    		// Log4j basic configuration
    		BasicConfigurator.configure();
    	
    		// java -cp carina-grid.jar com.qaprosoft.carina.grid.health.AppiumHealthServer localhost 8082 /Users/build/tools/grid
    		if(args.length > 0)
    		{
    			HOST = args[0];
    			PORT = args[1];
    			SCRIPTS = args[2];
    		}
    	
        Server server = new Server(Integer.valueOf(PORT));

        ServletHandler handler = new ServletHandler();
        server.setHandler(handler);
        handler.addServletWithMapping(NodesListServlet.class, "/nodes");
        handler.addServletWithMapping(NodesRestartServlet.class, "/nodes/restart");
        
        server.start();
        server.join();
    }

    /**
     * NodesListServlet - returns list of Appium processes.
     */
    public static class NodesListServlet extends HttpServlet
    {
		private static final long serialVersionUID = 595391156527488149L;

		@Override
        protected void doGet( HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
        {
			response.setContentType("text/html");
			response.setStatus(HttpServletResponse.SC_OK);
			response.getWriter().println("<meta http-equiv=\"refresh\" content=\"10\"/>");
			try
			{
				final String appiums = runScript("list_appium.sh");
				for(String appium : appiums.split("\n"))
                 {
                		String pid = appium.trim().split(" +")[1];
                		String udid = appium.trim().split("/configs/")[1].replace(".json", "");
                		response.getWriter().println(String.format("<b>%s</b> <span>%s</span> <a href=\"%s\">Restart</a><br/>", pid, udid, String.format("http://%s:%s/nodes/restart?udid=%s", HOST, PORT, udid)));
                 }
			}
			catch(Exception e)
			{
				response.setStatus(HttpServletResponse.SC_FORBIDDEN);
				response.getWriter().println("No appium processes found");
			} 
        }
    }
    
    /**
     * NodesRestartServlet - restarts Appium process.
     */
    public static class NodesRestartServlet extends HttpServlet
    {
		private static final long serialVersionUID = 760208753853575559L;

		@Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
        {
		    response.setContentType("text/html");
            response.setStatus(HttpServletResponse.SC_OK);
			try
			{
				final String appiums = runScript("list_appium.sh");
				final String udid = request.getParameter("udid");
	            for(String appium : appiums.split("\n"))
	            {
	            		if(appium.contains(udid))
	            		{
	            			String pid = appium.trim().split(" +")[1];
	            			runScript("restart_appium.sh", pid); 
	            			response.getWriter().println(String.format("<span>Restarting...</span><br/><a href=\"%s\">Go back</a>", String.format("http://%s:%s/nodes", HOST, PORT)));
	            			break;
	            		}
	            }
			}
			catch(Exception e)
			{
				response.setStatus(HttpServletResponse.SC_FORBIDDEN);
				response.getWriter().println(e.getMessage());
			}
        }
    }
    
    /**
     * Runs shell script and returns output.
     * @param script - name of shell script
     * @param args - script arguments
     * @return script output
     * @throws Exception - when any execution error take place
     */
    private static final String runScript(String script, String ... args) throws Exception
    {
    		String argString = "";
    		for(String arg : args)
    		{
    			argString += " " + arg;
    		}
    		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
	    CommandLine commandline = CommandLine.parse(SCRIPTS + File.separator + script + argString);
	    DefaultExecutor exec = new DefaultExecutor();
	    PumpStreamHandler streamHandler = new PumpStreamHandler(outputStream);
	    exec.setStreamHandler(streamHandler);
	    exec.execute(commandline);
	    return outputStream.toString();
    }
}