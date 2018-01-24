package com.qaprosoft.carina.grid.health;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.PumpStreamHandler;
import org.seleniumhq.jetty9.server.Server;
import org.seleniumhq.jetty9.servlet.ServletHandler;

/**
 * IOSHealthServer
 * 
 * @author Alex Khursevich (alex@qaprosoft.com)
 */
public class IOSHealthServer
{
	private static final String URL = "http://localhost:8082";
	private static final int PORT = 8082;
	
    public static void main(String[] args) throws Exception
    {
        Server server = new Server(PORT);

        ServletHandler handler = new ServletHandler();
        server.setHandler(handler);
        handler.addServletWithMapping(NodesListServlet.class, "/nodes");
        handler.addServletWithMapping(NodesRestartServlet.class, "/nodes/restart");
       
        
        server.start();
        server.join();
    }

    public static class NodesListServlet extends HttpServlet
    {
		private static final long serialVersionUID = 595391156527488149L;

		@Override
        protected void doGet( HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
        {
			ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		    CommandLine commandline = CommandLine.parse("/Users/akhursevich/git/carina/carina-grid/src/main/resources/scripts/list_appium.sh");
		    DefaultExecutor exec = new DefaultExecutor();
		    PumpStreamHandler streamHandler = new PumpStreamHandler(outputStream);
		    exec.setStreamHandler(streamHandler);
		    exec.execute(commandline);
		    
            response.setContentType("text/html");
            response.setStatus(HttpServletResponse.SC_OK);
            for(String line : outputStream.toString().split("\n"))
            {
            		String pid = line.trim().split(" +")[1];
            		String command = "node" + line.trim().split("node")[1];
            		response.getWriter().println(String.format("<meta http-equiv=\"refresh\" content=\"10\"/><b>%s</b> <span>%s</span> <a href=\"%s\">Restart</a><br/>", pid, command, URL + "/nodes/restart?pid=" + pid));
            }
        }
    }
    
    public static class NodesRestartServlet extends HttpServlet
    {
		private static final long serialVersionUID = 760208753853575559L;

		@Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException
        {
			ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		    CommandLine commandline = CommandLine.parse("/Users/akhursevich/git/carina/carina-grid/src/main/resources/scripts/kill_appium.sh " + request.getParameter("pid"));
		    DefaultExecutor exec = new DefaultExecutor();
		    PumpStreamHandler streamHandler = new PumpStreamHandler(outputStream);
		    exec.setStreamHandler(streamHandler);
		    exec.execute(commandline);
			
            response.setContentType("text/html");
            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().println(String.format("<span>Restarting...</span> <a href=\"%s\">Go back</a>", URL + "/nodes"));
        }
    }
}