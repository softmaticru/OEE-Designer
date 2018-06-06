package org.point85.app.designer;

import java.net.URL;

import org.point85.app.AppUtils;
import org.point85.app.FXMLLoaderFactory;
import org.point85.app.ImageManager;
import org.point85.app.Images;
import org.point85.app.dashboard.DashboardController;
import org.point85.app.dashboard.DashboardDialogController;
import org.point85.app.http.HttpServerController;
import org.point85.app.http.HttpTrendController;
import org.point85.app.material.MaterialEditorController;
import org.point85.app.messaging.MessagingTrendController;
import org.point85.app.messaging.MqBrokerController;
import org.point85.app.opc.da.OpcDaBrowserController;
import org.point85.app.opc.da.OpcDaTrendController;
import org.point85.app.opc.ua.OpcUaBrowserController;
import org.point85.app.opc.ua.OpcUaTreeNode;
import org.point85.app.opc.ua.OpcUaTrendController;
import org.point85.app.reason.ReasonEditorController;
import org.point85.app.schedule.WorkScheduleEditorController;
import org.point85.app.script.EventResolverController;
import org.point85.app.uom.UomConversionController;
import org.point85.app.uom.UomEditorController;
import org.point85.domain.collector.DataCollector;
import org.point85.domain.http.HttpSource;
import org.point85.domain.messaging.MessagingSource;
import org.point85.domain.opc.da.DaOpcClient;
import org.point85.domain.opc.da.OpcDaBrowserLeaf;
import org.point85.domain.opc.ua.UaOpcClient;
import org.point85.domain.persistence.PersistenceService;
import org.point85.domain.plant.Equipment;
import org.point85.domain.plant.Material;
import org.point85.domain.plant.PlantEntity;
import org.point85.domain.plant.Reason;
import org.point85.domain.schedule.WorkSchedule;
import org.point85.domain.script.EventResolver;
import org.point85.domain.script.OeeContext;
import org.point85.domain.uom.UnitOfMeasure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.SplitPane;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

public class DesignerApplication {
	// logger
	private static final Logger logger = LoggerFactory.getLogger(DesignerApplication.class);

	// physical model controller
	private PhysicalModelController physicalModelController;

	// reason editor controller
	private ReasonEditorController reasonController;

	// material editor controller
	private MaterialEditorController materialController;

	// UOM editor controller
	private UomEditorController uomController;

	// work schedule editor controller
	private WorkScheduleEditorController scheduleController;

	// OPC DA data source browser
	private OpcDaBrowserController opcDaBrowserController;

	// OPC UA data source browser
	private OpcUaBrowserController opcUaBrowserController;

	// HTTP server editor
	private HttpServerController httpServerController;

	// RabbitMQ broker editor
	private MqBrokerController mqBrokerController;

	// data collection definition
	private DataCollectorController dataCollectorController;

	// script resolver controller
	private EventResolverController scriptController;

	// UOM conversion controller
	private UomConversionController uomConversionController;

	// OEE dashboard controller
	private DashboardDialogController dashboardDialogController;

	// OPC DA trend
	private OpcDaTrendController opcDaTrendController;

	// OPC UA trend
	private OpcUaTrendController opcUaTrendController;

	// HTTP trend
	private HttpTrendController httpTrendController;

	// RMA messaging trend
	private MessagingTrendController messagingTrendController;

	// script execution context
	private OeeContext appContext;

	public DesignerApplication() {
		// nothing to initialize
	}

	public void start(Stage primaryStage) {
		try {
			// Load root layout from fxml file.
			FXMLLoader loader = new FXMLLoader();
			URL url = getClass().getResource("DesignerApplication.fxml");
			loader.setLocation(url);

			if (logger.isInfoEnabled()) {
				logger.info("Loading fxml at url " + url.toExternalForm());
			}

			AnchorPane mainLayout = (AnchorPane) loader.load();

			// Give the controller access to the main app.
			physicalModelController = loader.getController();
			physicalModelController.initialize(this);

			// create application context
			appContext = new OeeContext();

			// Show the scene containing the root layout.
			Scene scene = new Scene(mainLayout);

			// UI
			primaryStage.setTitle("OEE Designer");
			primaryStage.getIcons().add(ImageManager.instance().getImage(Images.POINT85));
			primaryStage.setScene(scene);
			primaryStage.show();

			if (logger.isInfoEnabled()) {
				logger.info("Populating top entity nodes.");
			}

			int populate = 1;

			if (populate == 1) {
				Platform.runLater(() -> {
					try {
						physicalModelController.populateTopEntityNodes();
					} catch (Exception e) {
						AppUtils.showErrorDialog(
								"Unable to fetch plant entities.  Check database connection.  " + e.getMessage());
					}
				});
			} else if (populate == 2) {
				try {
					physicalModelController.populateTopEntityNodes();
				} catch (Exception e) {
					AppUtils.showErrorDialog(
							"Unable to fetch plant entities.  Check database connection.  " + e.getMessage());
				}
			}

		} catch (Exception e) {
			logger.error(e.getMessage());
			stop();
		}
	}

	public void stop() {
		try {
			// JPA service
			PersistenceService.instance().close();

			// OPC DA
			if (getOpcDaClient() != null) {
				getOpcDaClient().disconnect();
			}

			// OPC UA
			if (getOpcUaClient() != null) {
				getOpcUaClient().disconnect();
			}
		} catch (Exception e) {
			logger.error(e.getMessage());
		}
	}

	// display the reason editor as a dialog
	public Reason showReasonEditor() throws Exception {
		// Load the fxml file and create a new stage for the pop-up dialog.
		if (reasonController == null) {
			FXMLLoader loader = FXMLLoaderFactory.reasonEditorLoader();
			AnchorPane page = (AnchorPane) loader.getRoot();

			// Create the dialog Stage.
			Stage dialogStage = new Stage(StageStyle.DECORATED);
			dialogStage.setTitle("Edit Reason");
			dialogStage.initModality(Modality.NONE);
			Scene scene = new Scene(page);
			dialogStage.setScene(scene);

			// get the controller
			reasonController = loader.getController();
			reasonController.setDialogStage(dialogStage);
			reasonController.initialize(this);
		}

		// Show the dialog and wait until the user closes it
		if (!reasonController.getDialogStage().isShowing()) {
			reasonController.getDialogStage().showAndWait();
		}

		return reasonController.getSelectedReason();
	}

	// display the material editor as a dialog
	public Material showMaterialEditor() throws Exception {
		if (this.materialController == null) {
			FXMLLoader loader = FXMLLoaderFactory.materialEditorLoader();
			AnchorPane pane = (AnchorPane) loader.getRoot();

			// Create the dialog Stage.
			Stage dialogStage = new Stage(StageStyle.DECORATED);
			dialogStage.setTitle("Edit Material");
			dialogStage.initModality(Modality.NONE);

			Scene scene = new Scene(pane);
			dialogStage.setScene(scene);

			// get the controller
			materialController = loader.getController();
			materialController.setDialogStage(dialogStage);
			materialController.initialize(this);
		}

		// Show the dialog and wait until the user closes it
		if (!materialController.getDialogStage().isShowing()) {
			materialController.getDialogStage().showAndWait();
		}

		return materialController.getSelectedMaterial();
	}

	// display the UOM editor as a dialog
	public UnitOfMeasure showUomEditor() throws Exception {
		if (this.uomController == null) {
			// Load the fxml file and create a new stage for the pop-up dialog.
			FXMLLoader loader = FXMLLoaderFactory.uomEditorLoader();
			AnchorPane page = (AnchorPane) loader.getRoot();

			// Create the dialog Stage
			Stage dialogStage = new Stage(StageStyle.DECORATED);
			dialogStage.setTitle("Edit Unit Of Measure");
			dialogStage.initModality(Modality.NONE);

			Scene scene = new Scene(page);
			dialogStage.setScene(scene);

			// get the controller
			uomController = loader.getController();
			uomController.setDialogStage(dialogStage);

			uomController.initializeApp(this);
		}

		// Show the dialog and wait until the user closes it
		if (!uomController.getDialogStage().isShowing()) {
			uomController.getDialogStage().showAndWait();
		}

		return uomController.getSelectedUom();
	}

	// display the work schedule editor as a dialog
	WorkSchedule showScheduleEditor() throws Exception {
		if (this.scheduleController == null) {
			// Load the fxml file and create a new stage for the pop-up dialog.
			FXMLLoader loader = FXMLLoaderFactory.scheduleEditorLoader();
			AnchorPane page = (AnchorPane) loader.getRoot();

			// Create the dialog Stage.
			Stage dialogStage = new Stage(StageStyle.DECORATED);
			dialogStage.setTitle("Edit Work Schedule");
			dialogStage.initModality(Modality.NONE);

			Scene scene = new Scene(page);
			dialogStage.setScene(scene);

			// get the controller
			scheduleController = loader.getController();
			scheduleController.setDialogStage(dialogStage);
			scheduleController.initializeApp(this);
		}

		// Show the dialog and wait until the user closes it
		if (!scheduleController.getDialogStage().isShowing()) {
			scheduleController.getDialogStage().showAndWait();
		}

		return scheduleController.getSelectedSchedule();
	}

	OpcDaBrowserLeaf showOpcDaDataSourceBrowser() throws Exception {
		if (opcDaBrowserController == null) {
			// Load the fxml file and create a new stage for the pop-up dialog.
			FXMLLoader loader = FXMLLoaderFactory.opdDaBrowserLoader();
			AnchorPane page = (AnchorPane) loader.getRoot();

			// Create the dialog Stage.
			Stage dialogStage = new Stage(StageStyle.DECORATED);
			dialogStage.setTitle("Browse OPC DA Data Source");
			dialogStage.initModality(Modality.NONE);

			Scene scene = new Scene(page);
			dialogStage.setScene(scene);

			// get the controller
			opcDaBrowserController = loader.getController();
			opcDaBrowserController.setDialogStage(dialogStage);
			opcDaBrowserController.initialize(this);
		}

		// Show the dialog and wait until the user closes it
		if (!opcDaBrowserController.getDialogStage().isShowing()) {
			opcDaBrowserController.getDialogStage().showAndWait();
		}

		return opcDaBrowserController.getSelectedTag();
	}

	OpcUaTreeNode showOpcUaDataSourceBrowser() throws Exception {
		if (opcUaBrowserController == null) {
			// Load the fxml file and create a new stage for the pop-up dialog.
			FXMLLoader loader = FXMLLoaderFactory.opdUaBrowserLoader();
			AnchorPane page = (AnchorPane) loader.getRoot();

			// Create the dialog Stage.
			Stage dialogStage = new Stage(StageStyle.DECORATED);
			dialogStage.setTitle("Browse OPC UA Data Source");
			dialogStage.initModality(Modality.NONE);

			Scene scene = new Scene(page);
			dialogStage.setScene(scene);

			// get the controller
			opcUaBrowserController = loader.getController();
			opcUaBrowserController.setDialogStage(dialogStage);
			opcUaBrowserController.initialize(this);
		}

		// Show the dialog and wait until the user closes it
		if (!opcUaBrowserController.getDialogStage().isShowing()) {
			opcUaBrowserController.getDialogStage().showAndWait();
		}

		return opcUaBrowserController.getSelectedNodeId();
	}

	String showScriptEditor(EventResolver eventResolver) throws Exception {
		// Load the fxml file and create a new stage for the pop-up dialog.
		if (scriptController == null) {
			FXMLLoader loader = FXMLLoaderFactory.eventResolverLoader();
			AnchorPane page = (AnchorPane) loader.getRoot();

			// Create the dialog Stage.
			Stage dialogStage = new Stage(StageStyle.DECORATED);
			dialogStage.setTitle("Edit Script Resolver");
			dialogStage.initModality(Modality.NONE);

			Scene scene = new Scene(page);
			dialogStage.setScene(scene);

			// get the controller
			scriptController = loader.getController();
			scriptController.setDialogStage(dialogStage);
			scriptController.initialize(this, eventResolver);
		}

		// display current script
		scriptController.showFunctionScript(eventResolver);

		// Show the dialog and wait until the user closes it
		if (!scriptController.getDialogStage().isShowing()) {
			scriptController.getDialogStage().showAndWait();
		}

		return scriptController.getResolver().getScript();
	}

	HttpSource showHttpServerEditor() throws Exception {
		if (httpServerController == null) {
			FXMLLoader loader = FXMLLoaderFactory.httpServerLoader();
			AnchorPane page = (AnchorPane) loader.getRoot();

			// Create the dialog Stage.
			Stage dialogStage = new Stage(StageStyle.DECORATED);
			dialogStage.setTitle("Edit HTTP Servers");
			dialogStage.initModality(Modality.WINDOW_MODAL);
			Scene scene = new Scene(page);
			dialogStage.setScene(scene);

			// get the controller
			httpServerController = loader.getController();
			httpServerController.setDialogStage(dialogStage);
			httpServerController.initializeServer();
		}

		// Show the dialog and wait until the user closes it
		httpServerController.getDialogStage().showAndWait();

		return httpServerController.getSource();
	}

	MessagingSource showRmqBrokerEditor() throws Exception {
		if (mqBrokerController == null) {
			FXMLLoader loader = FXMLLoaderFactory.mqBrokerLoader();
			AnchorPane page = (AnchorPane) loader.getRoot();

			// Create the dialog Stage.
			Stage dialogStage = new Stage(StageStyle.DECORATED);
			dialogStage.setTitle("Edit RabbitMQ Brokers");
			dialogStage.initModality(Modality.WINDOW_MODAL);
			Scene scene = new Scene(page);
			dialogStage.setScene(scene);

			// get the controller
			mqBrokerController = loader.getController();
			mqBrokerController.setDialogStage(dialogStage);
			mqBrokerController.initialize(this);
		}

		// Show the dialog and wait until the user closes it
		if (!mqBrokerController.getDialogStage().isShowing()) {
			mqBrokerController.getDialogStage().showAndWait();
		}

		return mqBrokerController.getSource();
	}

	DataCollector showCollectorEditor() throws Exception {
		if (dataCollectorController == null) {
			FXMLLoader loader = FXMLLoaderFactory.dataCollectorLoader();
			AnchorPane page = (AnchorPane) loader.getRoot();

			// Create the dialog Stage.
			Stage dialogStage = new Stage(StageStyle.DECORATED);
			dialogStage.setTitle("Edit Collector Configurations");
			dialogStage.initModality(Modality.WINDOW_MODAL);
			Scene scene = new Scene(page);
			dialogStage.setScene(scene);

			// get the controller
			dataCollectorController = loader.getController();
			dataCollectorController.setDialogStage(dialogStage);
			dataCollectorController.initialize(this);
		}

		// Show the dialog and wait until the user closes it
		if (!dataCollectorController.getDialogStage().isShowing()) {
			dataCollectorController.getDialogStage().showAndWait();
		}

		return dataCollectorController.getCollector();
	}

	void showUomConverter() throws Exception {
		if (uomConversionController == null) {
			FXMLLoader loader = FXMLLoaderFactory.uomConversionLoader();
			AnchorPane page = (AnchorPane) loader.getRoot();

			// Create the dialog stage
			Stage dialogStage = new Stage(StageStyle.DECORATED);
			dialogStage.setTitle("Unit of Measure Converter");
			dialogStage.initModality(Modality.NONE);
			Scene scene = new Scene(page);
			dialogStage.setScene(scene);

			// get the controller
			uomConversionController = loader.getController();
			uomConversionController.setDialogStage(dialogStage);
			uomConversionController.initializeApp(this);
		}

		// Show the dialog and wait until the user closes it
		if (!uomConversionController.getDialogStage().isShowing()) {
			uomConversionController.getDialogStage().showAndWait();
		}
	}

	void showOpcDaTrendDialog(EventResolver eventResolver) throws Exception {
		if (opcDaTrendController == null) {
			// Load the fxml file and create a new stage for the pop-up dialog.
			FXMLLoader loader = FXMLLoaderFactory.opcDaTrendLoader();
			AnchorPane page = (AnchorPane) loader.getRoot();

			// Create the dialog Stage.
			Stage dialogStage = new Stage(StageStyle.DECORATED);
			dialogStage.setTitle("OPC DA Item Trend");
			dialogStage.initModality(Modality.NONE);
			Scene scene = new Scene(page);
			dialogStage.setScene(scene);

			// get the controller
			opcDaTrendController = loader.getController();
			opcDaTrendController.setDialogStage(dialogStage);
			opcDaTrendController.setApp(this);

			// add the trend chart
			SplitPane chartPane = opcDaTrendController.initializeTrend();

			AnchorPane.setBottomAnchor(chartPane, 50.0);
			AnchorPane.setLeftAnchor(chartPane, 5.0);
			AnchorPane.setRightAnchor(chartPane, 5.0);
			AnchorPane.setTopAnchor(chartPane, 50.0);

			page.getChildren().add(0, chartPane);
		}

		// set the script resolver
		opcDaTrendController.setScriptResolver(eventResolver);

		// show the window
		if (!opcDaTrendController.getDialogStage().isShowing()) {
			opcDaTrendController.getDialogStage().show();
		}
	}

	void showOpcUaTrendDialog(EventResolver eventResolver) throws Exception {
		if (opcUaTrendController == null) {
			// Load the fxml file and create a new stage for the pop-up dialog.
			FXMLLoader loader = FXMLLoaderFactory.opcUaTrendLoader();
			AnchorPane page = (AnchorPane) loader.getRoot();

			// Create the dialog Stage.
			Stage dialogStage = new Stage(StageStyle.DECORATED);
			dialogStage.setTitle("OPC UA Item Trend");
			dialogStage.initModality(Modality.NONE);
			// dialogStage.initOwner(primaryStage);
			Scene scene = new Scene(page);
			dialogStage.setScene(scene);

			// get the controller
			opcUaTrendController = loader.getController();
			opcUaTrendController.setDialogStage(dialogStage);
			opcUaTrendController.setApp(this);

			// add the trend chart
			SplitPane chartPane = opcUaTrendController.initializeTrend();

			opcUaTrendController.setUpdatePeriodMsec(eventResolver.getUpdatePeriod());

			AnchorPane.setBottomAnchor(chartPane, 50.0);
			AnchorPane.setLeftAnchor(chartPane, 5.0);
			AnchorPane.setRightAnchor(chartPane, 5.0);
			AnchorPane.setTopAnchor(chartPane, 50.0);

			page.getChildren().add(0, chartPane);
		}

		// set the script resolver
		opcUaTrendController.setScriptResolver(eventResolver);

		// show the window
		if (!opcUaTrendController.getDialogStage().isShowing()) {
			opcUaTrendController.getDialogStage().show();
		}
	}

	void showHttpTrendDialog(EventResolver eventResolver) throws Exception {
		if (httpTrendController == null) {
			// Load the fxml file and create a new stage for the pop-up dialog.
			FXMLLoader loader = FXMLLoaderFactory.httpTrendLoader();
			AnchorPane page = (AnchorPane) loader.getRoot();

			// Create the dialog Stage.
			Stage dialogStage = new Stage(StageStyle.DECORATED);
			dialogStage.setTitle("HTTP Event Trend");
			dialogStage.initModality(Modality.NONE);
			Scene scene = new Scene(page);
			dialogStage.setScene(scene);

			// get the controller
			httpTrendController = loader.getController();
			httpTrendController.setDialogStage(dialogStage);
			httpTrendController.setApp(this);

			// add the trend chart
			SplitPane chartPane = httpTrendController.initializeTrend();

			AnchorPane.setBottomAnchor(chartPane, 50.0);
			AnchorPane.setLeftAnchor(chartPane, 5.0);
			AnchorPane.setRightAnchor(chartPane, 5.0);
			AnchorPane.setTopAnchor(chartPane, 50.0);

			page.getChildren().add(0, chartPane);
		}

		// set the script resolver
		httpTrendController.setScriptResolver(eventResolver);

		// start HTTP server
		httpTrendController.onStartServer();

		// show the trend
		if (!httpTrendController.getDialogStage().isShowing()) {
			httpTrendController.getDialogStage().show();
		}
	}

	void showMessagingTrendDialog(EventResolver eventResolver) throws Exception {
		if (messagingTrendController == null) {
			// Load the fxml file and create a new stage for the pop-up dialog.
			FXMLLoader loader = FXMLLoaderFactory.messagingTrendLoader();
			AnchorPane page = (AnchorPane) loader.getRoot();

			// Create the dialog Stage.
			Stage dialogStage = new Stage(StageStyle.DECORATED);
			dialogStage.setTitle("Messaging Event Trend");
			dialogStage.initModality(Modality.NONE);
			Scene scene = new Scene(page);
			dialogStage.setScene(scene);

			// get the controller
			messagingTrendController = loader.getController();
			messagingTrendController.setDialogStage(dialogStage);
			messagingTrendController.setApp(this);

			// add the trend chart
			SplitPane chartPane = messagingTrendController.initializeTrend();

			AnchorPane.setBottomAnchor(chartPane, 50.0);
			AnchorPane.setLeftAnchor(chartPane, 5.0);
			AnchorPane.setRightAnchor(chartPane, 5.0);
			AnchorPane.setTopAnchor(chartPane, 50.0);

			page.getChildren().add(0, chartPane);
		}

		// set the script resolver
		messagingTrendController.setEventResolver(eventResolver);

		// subscribe to broker
		messagingTrendController.subscribeToDataSource();

		// show the window
		if (!messagingTrendController.getDialogStage().isShowing()) {
			messagingTrendController.getDialogStage().show();
		}
	}

	public PhysicalModelController getPhysicalModelController() {
		return this.physicalModelController;
	}

	public DaOpcClient getOpcDaClient() {
		if (appContext == null) {
			return null;
		}

		DaOpcClient client = appContext.getOpcDaClient();

		if (client == null) {
			client = new DaOpcClient();
			appContext.getOpcDaClients().add(client);
		}
		return client;
	}

	OpcDaBrowserController getOpcDaBrowserController() {
		return this.opcDaBrowserController;
	}

	OpcUaBrowserController getOpcUaBrowserController() {
		return this.opcUaBrowserController;
	}

	public OeeContext getAppContext() {
		return appContext;
	}

	// display the OEE dashboard as a dialog
	void showDashboard() throws Exception {
		PlantEntity entity = getPhysicalModelController().getSelectedEntity();

		if (dashboardDialogController == null) {
			FXMLLoader dialogLoader = FXMLLoaderFactory.dashboardDialogLoader();
			AnchorPane pane = (AnchorPane) dialogLoader.getRoot();

			// Create the dialog Stage.
			Stage dialogStage = new Stage(StageStyle.DECORATED);
			dialogStage.setTitle("OEE Dashboard for " + entity.getDisplayString());
			dialogStage.initModality(Modality.NONE);
			Scene scene = new Scene(pane);
			dialogStage.setScene(scene);

			// get the controller
			dashboardDialogController = dialogLoader.getController();
			dashboardDialogController.setDialogStage(dialogStage);

			// load the content
			FXMLLoader dashboardLoader = FXMLLoaderFactory.dashboardLoader();
			SplitPane spDashboard = (SplitPane) dashboardLoader.getRoot();

			pane.getChildren().add(0, spDashboard);

			AnchorPane.setTopAnchor(spDashboard, 0.0);
			AnchorPane.setBottomAnchor(spDashboard, 50.0);
			AnchorPane.setLeftAnchor(spDashboard, 0.0);
			AnchorPane.setRightAnchor(spDashboard, 0.0);

			DashboardController dashboardController = dashboardLoader.getController();
			dashboardController.enableRefresh(true);

			dashboardDialogController.setDashboardController(dashboardController);
		}
		dashboardDialogController.getDashboardController().setupEquipmentLoss((Equipment) entity);

		// Show the dialog and wait until the user closes it
		if (!dashboardDialogController.getDialogStage().isShowing()) {
			dashboardDialogController.getDialogStage().showAndWait();
		}
	}

	public UaOpcClient getOpcUaClient() {
		if (appContext == null) {
			return null;
		}

		UaOpcClient client = appContext.getOpcUaClient();

		if (client == null) {
			client = new UaOpcClient();
			appContext.getOpcUaClients().add(client);
		}
		return client;
	}
}
