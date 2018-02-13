package com.deviceCloud;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.remote.DesiredCapabilities;

import com.ssts.pcloudy.Connector;
import com.ssts.pcloudy.Version;
import com.ssts.pcloudy.appium.PCloudyAppiumSession;
import com.ssts.pcloudy.dto.appium.booking.BookingDtoDevice;
import com.ssts.pcloudy.dto.device.MobileDevice;
import com.ssts.pcloudy.dto.file.PDriveFileDTO;
import com.ssts.pcloudy.ConnectError;
import com.ssts.util.reporting.ExecutionResult;
import com.ssts.util.reporting.MultipleRunReport;
import com.ssts.util.reporting.SingleRunReport;
import com.ssts.util.reporting.printers.HtmlFilePrinter;

import io.appium.java_client.AppiumDriver;
import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.ios.IOSDriver;

public class Controller {

public File ReportsFolder = new File("Reports");
	

	public static void main(String[] args) throws IOException, ConnectError, InterruptedException {

		Controller controller = new Controller();
		controller.runExecutionOnPCloudy();

		// TODO Auto-generated method stub

	}

	public void runExecutionOnPCloudy() throws IOException, ConnectError, InterruptedException {
		Connector con = new Connector("https://device.pcloudy.com/api/");

		// User Authentication over pCloudy
		String authToken = con.authenticateUser("shibu.prasad@sstsinc.com", "5vgzqqp4zrd2hdrgymbqz8yq");

		ArrayList<MobileDevice> selectedDevices = new ArrayList<>();
		
		// To select multiple devices manually, use either of these:
		selectedDevices.addAll(con.chooseMultipleDevices(authToken, "iOS"));
		// selectedDevices.addAll(CloudyCONNECTOR.chooseSingleDevice(authToken, "android"));

		String sessionName = selectedDevices.get(0).display_name + " Appium Session";
		// Book the selected devices in pCloudy
		BookingDtoDevice[] bookedDevices = con.AppiumApis().bookDevicesForAppium(authToken, selectedDevices, 10, sessionName);
		System.out.println("Devices booked successfully");

		// Select apk in pCloudy Cloud Drive
		File fileToBeUploaded = new File("./TestmunkDemo.ipa");
		PDriveFileDTO alreadyUploadedApp = con.getAvailableAppIfUploaded(authToken, fileToBeUploaded.getName());
		if (alreadyUploadedApp == null) {
			System.out.println("Uploading App: " + fileToBeUploaded.getAbsolutePath());
			PDriveFileDTO uploadedApp = con.uploadApp(authToken, fileToBeUploaded, false);
			System.out.println("App uploaded");
			alreadyUploadedApp = new PDriveFileDTO();
			alreadyUploadedApp.file = uploadedApp.file;
		} else {
			System.out.println(" App already present. Not uploading... ");
		}

		con.AppiumApis().initAppiumHubForApp(authToken, alreadyUploadedApp);

		URL endpoint = con.AppiumApis().getAppiumEndpoint(authToken);
		System.out.println("Appium Endpoint: " + endpoint);

		URL reportFolderOnPCloudy = con.AppiumApis().getAppiumReportFolder(authToken);
		System.out.println("Report Folder: " + reportFolderOnPCloudy);

		List<Thread> allThreads = new ArrayList<Thread>();
		MultipleRunReport multipleReports = new MultipleRunReport();
		multipleReports.ProjectLogo = "https://devicecloud.bosch.com/nimages/newlogo.png";
		multipleReports.pCloudyLogo="https://devicecloud.bosch.com/nimages/newlogo.png";

		// Create multiple driver objects in multiple threads
		for (int i = 0; i < bookedDevices.length; i++) {
			BookingDtoDevice aDevice = bookedDevices[i];
			PCloudyAppiumSession pCloudySession = new PCloudyAppiumSession(con, authToken, aDevice);
			SingleRunReport report = new SingleRunReport();
			multipleReports.add(report);

			report.pCloudyLogo="https://devicecloud.bosch.com/nimages/newlogo.png";
			report.ProjectLogo="https://devicecloud.bosch.com/nimages/newlogo.png";
			report.Header = aDevice.manufacturer + " " + aDevice.model + " " + aDevice.version;
			report.Enviroment.addDetail("NetworkType", aDevice.networkType);
			report.Enviroment.addDetail("Phone Number", aDevice.phoneNumber);
			report.HyperLinks.addLink("Appium Endpoint", endpoint);
			report.HyperLinks.addLink("pCloudy Result Folder", reportFolderOnPCloudy);

			Runnable testCase = getTestCaseClass(endpoint, aDevice, pCloudySession, report);
			Thread aThread = new Thread(testCase);
			aThread.start();
			allThreads.add(aThread);
		}

		for (Thread aThread : allThreads) {
			aThread.join();
		}

		con.revokeTokenPrivileges(authToken);

		File consolidatedReport = new File(ReportsFolder, "ConsolidatedReports.html");
		HtmlFilePrinter printer = new HtmlFilePrinter(consolidatedReport);
		printer.printConsolidatedSingleRunReport(multipleReports);
		System.out.println("Check the reports at : " + consolidatedReport.getAbsolutePath());

		System.out.println("Execution Completed...");
	}

	private Runnable getTestCaseClass(final URL endpoint, final BookingDtoDevice aDevice, final PCloudyAppiumSession pCloudySession, final SingleRunReport report) {
		// this will give a Thread Safe TestScript class.
		// You may also like to have this as a named class in a separate file

		return new Runnable() {

			@Override
			public void run() {
				File deviceFolder = new File(ReportsFolder, aDevice.manufacturer + " " + aDevice.model + " " + aDevice.version);
				File snapshotsFolder = new File(deviceFolder, "Snapshots");
				snapshotsFolder.mkdirs();
				try {
					DesiredCapabilities capabilities = new DesiredCapabilities();
					capabilities.setCapability("newCommandTimeout", 600);
					capabilities.setCapability("launchTimeout", 90000);
					capabilities.setCapability("deviceName", aDevice.capabilities.deviceName);
					capabilities.setCapability("browserName", aDevice.capabilities.browserName);
					capabilities.setCapability("platformName", "iOS");
					capabilities.setCapability("bundleId", "com.pcloudy.TestmunkDemo");

					capabilities.setCapability("usePrebuiltWDA", false);
					capabilities.setCapability("acceptAlerts", true);

					if (aDevice.getVersion().compareTo(new Version("9.3")) >= 0)
						capabilities.setCapability("automationName", "XCUITest");
					else
						capabilities.setCapability("automationName", "Appium");
					AppiumDriver driver = new IOSDriver(endpoint, capabilities);

					report.beginTestcase("TestCase BA");
					report.addStep("Launch App", capabilities.toString(), endpoint.toString(), ExecutionResult.Pass);

					report.addComment("--- Add your Test Scripts over here ---");
					if (pCloudySession.getDto().getVersion().compareTo(new Version("9.3")) >= 0) {
						driver.findElement(By.xpath("//XCUIElementTypeApplication[1]/XCUIElementTypeWindow[1]/XCUIElementTypeOther[1]/XCUIElementTypeTextField[1]")).sendKeys("test@testname.com");
					}
					
					// ###########################################
					// ###########################################
					// ###########################################
					// ###########################################
					// Your Test Script Goes Here
					// ###########################################
					// ###########################################
					// ###########################################
					// ###########################################
					// ###########################################

					File snapshotTmpFile = pCloudySession.takeScreenshot();
					File snapshotFile = new File(snapshotsFolder, snapshotTmpFile.getName());
					FileUtils.moveFile(snapshotTmpFile, snapshotFile);

					report.addStep("Take Screenshot", null, null, snapshotFile.getAbsolutePath(), ExecutionResult.Pass);

					// release session now
					pCloudySession.releaseSessionNow();

					report.addStep("Release Appium Session", null, null, ExecutionResult.Pass);

				} catch (Exception e) {
					report.addStep("Error Running TestCase", null, e.getMessage(), ExecutionResult.Fail);
					e.printStackTrace();
				} finally {
					HtmlFilePrinter printer = new HtmlFilePrinter(new File(deviceFolder, deviceFolder.getName() + ".html"));
					printer.printSingleRunReport(report);

				}
			}

		};
	}

	
}
