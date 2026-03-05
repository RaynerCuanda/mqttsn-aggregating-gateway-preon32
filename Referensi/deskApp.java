import com.virtenio.commander.io.DataConnection;
import com.virtenio.commander.toolsets.preon32.Preon32Helper;
import com.fazecast.jSerialComm.SerialPort;
//import com.virtenio.commander.toolsets.preon32.Preon32Tools;
//import com.virtenio.commander.misc.JavaProcess;
//import com.virtenio.commander.comportserver.ClientDataWorker;
//import com.virtenio.commander.toolsets.common.ModuleManager;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.ProjectHelper;
import org.apache.tools.ant.DefaultLogger;
import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.*;
import java.util.*;
import java.io.File;


public class deskApp {
	Calendar cal = Calendar.getInstance();
	
	private BufferedWriter writer;
	private Scanner choice; 
	private volatile boolean exit = false;
	
	Preon32Helper c;
	private ArrayList<SerialPort> listSerialPorts;
	
	public void writeToFileRHT(String fName, String folName, BufferedInputStream in) throws Exception
	{
		new Thread() 
		{	
			byte[] buffer = new byte[2048];
			String s; long count=0;
			File newFolder = new File(folName);
			public void run()
			{
				// disini check apakah sdh ada direktori hari ini
				// jika belum dibuat
				// maka semua file harus berada di direktori hari ini.
				if (!newFolder.exists()) newFolder.mkdir();
				String path = folName + "/" + fName;
				try {
					FileWriter fw = new FileWriter(path);
					writer = new BufferedWriter(fw);
				} catch (Exception e) { e.printStackTrace();}
				while (!exit) 
				{
					try {
						in.read(buffer);
						s = new String(buffer);	
					//	System.out.println(s);
						String[] subStr=s.split("#");
						for (String w:subStr) {
    						if (w.startsWith("SENSE")) {
    							if (w.contains("RHT"))
    								writer.write(w,0, w.length());
    						}
						}
						count++;
						if (count==100)
						{
							writer.close();
							FileWriter fw = new FileWriter(path,true);
							writer = new BufferedWriter(fw);
							count = 0;
						}
					} catch (IOException e) {}
					
					Arrays.fill(buffer, (byte) 0);
				}
				try {
					writer.close();
				} catch (Exception e) { e.printStackTrace();}
			}
		}.start();
	}
	
	public void writeToFile(String fName, String folName, BufferedInputStream in) throws Exception
	{
		new Thread() 
		{	
			byte[] buffer = new byte[256];
			String s; long count=0;
			File newFolder = new File(folName);
			public void run()
			{
				// disini check apakah sdh ada direktori hari ini
				// jika belum dibuat
				// maka semua file harus berada di direktori hari ini.
				if (!newFolder.exists()) newFolder.mkdir();
				String path = folName + "/" + fName;
				try {
					FileWriter fw = new FileWriter(path);
					writer = new BufferedWriter(fw);
				} catch (Exception e) { e.printStackTrace();}
				while (!exit) 
				{
					try {
						in.read(buffer);
						s = new String(buffer);	
						//System.out.println(s);
						String[] subStr=s.split("#");
						for (String w:subStr) {
    						if (w.startsWith("SENSE"))
    								writer.write(w,0, w.length()); 
						}
						count++;
						if (count==100)
						{
							// critical section
							writer.close();
							FileWriter fw = new FileWriter(path,true);
							writer = new BufferedWriter(fw);
							count = 0;
						}
					} catch (IOException e) {}
					
					Arrays.fill(buffer, (byte) 0);
				}
				try {
					writer.close();
				} catch (Exception e) { e.printStackTrace();}
			}
		}.start();
	}
	
	
	private void context_set(String target) throws Exception
	{
		DefaultLogger consoleLogger = getConsoleLogger();
		// Prepare ant project
		File buildFile = new File ("D:\\Share from ThinkPad\\eclipse-workspace\\baseStationWSN\\buildUser.xml");
		Project antProject = new Project();
		antProject.setUserProperty("ant.file", buildFile.getAbsolutePath());
		antProject.addBuildListener(consoleLogger);
		
		try {
			antProject.fireBuildStarted();
			antProject.init();
			ProjectHelper helper = ProjectHelper.getProjectHelper();
			antProject.addReference("ant.ProjectHelper", helper);
			helper.parse(antProject, buildFile);
			//
			antProject.executeTarget(target);
			antProject.fireBuildFinished(null);
		} catch (BuildException e) { e.printStackTrace();}
	}

	private void time_synchronize() throws Exception
	{
		DefaultLogger consoleLogger = getConsoleLogger();
		File buildFile = new File ("C:\\Users\\elisatih\\eclipse-workspace\\rdtWSN\build.xml");
		Project antProject = new Project();
		antProject.setUserProperty("ant.file", buildFile.getAbsolutePath());
		antProject.addBuildListener(consoleLogger);
		
		try {
			antProject.fireBuildStarted();
			antProject.init();
			ProjectHelper helper = ProjectHelper.getProjectHelper();
			antProject.addReference("ant.ProjectHelper", helper);
			helper.parse(antProject, buildFile);
			//
			String target = "cmd.time.synchronize";
			antProject.executeTarget(target);
			antProject.fireBuildFinished(null);
		} catch (BuildException e) { e.printStackTrace();}
	}

	public void init() throws Exception 
	{
		stringFormatTime sfTime = new stringFormatTime();
		try 
		{
			SerialPort[] arrSerialPort = SerialPort.getCommPorts();	
			this.listSerialPorts = this.filterPort(arrSerialPort, "Preon32");
			
			for (SerialPort serialport : this.listSerialPorts) {
				System.out.println(serialport.getSystemPortName());
			}
			
			Preon32Helper nodeHelper = new Preon32Helper("COM10",115200); 
			DataConnection conn = nodeHelper.runModule("progBS"); // "bStation

					
			BufferedInputStream in = new BufferedInputStream(conn.getInputStream());

			
			//BufferedOutputStream out = new BufferedOutputStream(conn.getOutputStream());
			int choiceentry;
			int[] menuChoice	= {0,0,0,0,0,0};
			//byte[] buffer = new byte[2048];
			String s;
			choice = new Scanner(System.in);
			/// START MENU
			conn.flush();
			do 
			{ 
				System.out.println("MENU");
				System.out.println("1. Hi all"); // utk mendapatkan rata2 delay RTT
	    		System.out.println("2. Set Your Time");
	    		System.out.println("3. Please Tell Your Time");
	    		System.out.println("4. Go Sense NOW!");
	    		System.out.println("5. Go Sense Humidity Temperature");
	    		System.out.println("6. Go Sensr Temp Hum Pressure - local");
	    		System.out.println("0. Exit");
	    		System.out.println("Choice: ");
	    		
	    		choiceentry = choice.nextInt();
	    		conn.write(choiceentry); 
	    		Thread.sleep(200); 
	    		switch (choiceentry) 
	    		{
	    			case 0:
	    			{ 
	    				exit = true;
	    				break;
	    			}
	    			case 1: 
	    			{
	    				byte[] buffer = new byte[1024];
	    				while( in.available() > 0) {
	    					in.read(buffer);
	    					s = new String(buffer);
	    					conn.flush();
	    					String[] subStr=s.split("#");
	    					for (String w:subStr) {
	    						if (w.startsWith("HELLO"))
	    							System.out.println(w);
	    						else if (w.startsWith("RSSI"))
	    							System.out.println(w);
	    					}
	    				}
	    				break;
	    			}
					case 2: 
					{	
						byte[] buffer = new byte[1024];
						while ( in.available() > 0) { 
							in.read(buffer);
							conn.flush();
							s = new String(buffer);	
							String[] subStr=s.split("#");
							for (String w:subStr) {
	    						if (w.startsWith("SET"))
	    							System.out.println(w);
	    						else if (w.startsWith("RSSI"))
	    							System.out.println(w);
	    					}
						}
						break;
					}
					case 3: 
					{	
						byte[] buffer = new byte[1024];
						while ( in.available() > 0) {
							in.read(buffer);
							conn.flush();
							s = new String(buffer);
							String[] subStr=s.split("#");
							for (String w:subStr) {
	    						if (w.startsWith("NOW"))
	    							System.out.println(w);
	    						else if (w.startsWith("RSSI"))
	    							System.out.println(w);
							}
						}
						break;
					}
					case 4: 
					{
						long msecs = cal.getTimeInMillis();
						String fName = sfTime.SFFile(msecs);
						String folName = sfTime.SFDate(msecs);
						fName = "ACL_"+ fName + ".txt";
						writeToFile(fName, folName, in); // thread of void
						break;
					}
					case 5:
					{
						long msecs = cal.getTimeInMillis();
						String fName = sfTime.SFFile(msecs);
						String folName = sfTime.SFDate(msecs);
						fName = "RHT_"+ fName + ".txt";
						writeToFileRHT(fName, folName, in); // thread of void
						break;	
					}
					case 6:
					{
						long msecs = cal.getTimeInMillis();
						String fName = sfTime.SFFile(msecs);
						String folName = sfTime.SFDate(msecs);
						fName = "THP_"+ fName + ".txt";
						writeToFile(fName, folName, in); // thread of void
						break;
					}
				}   
	    		
			} while (choiceentry !=0);
			
		} catch (Exception e) 
			{ e.printStackTrace();
		} 
	}
	
	private static DefaultLogger getConsoleLogger() {
	        DefaultLogger consoleLogger = new DefaultLogger();
	        consoleLogger.setErrorPrintStream(System.err);
	        consoleLogger.setOutputPrintStream(System.out);
	        consoleLogger.setMessageOutputLevel(Project.MSG_INFO);
	         
	        return consoleLogger;
	}
	 
	private ArrayList<SerialPort> filterPort(SerialPort[] arr, String target) {
		ArrayList<SerialPort> res = new ArrayList<SerialPort>();
		for (SerialPort serialport : arr) {
			if (serialport.getDescriptivePortName().contains(target)) {
				res.add(serialport);
			}
		}

		return res;
	}
	
	public static void main (String[] args) throws Exception {
		deskApp aGet = new deskApp();
		
		aGet.context_set("context.set.2");
		aGet.time_synchronize();
		aGet.context_set("context.set.1");
		aGet.init();
	}
}