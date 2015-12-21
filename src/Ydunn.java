/**
 * Created by pilillo on 12/20/15.
 */


import com.bitalino.comm.BITalinoDevice;
import com.bitalino.comm.BITalinoException;
import com.bitalino.comm.BITalinoFrame;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.data.time.Millisecond;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;

import javax.bluetooth.*;
import javax.microedition.io.Connector;
import javax.microedition.io.StreamConnection;
import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Vector;

public class Ydunn extends JFrame implements DiscoveryListener, WindowListener, ActionListener, Runnable{

    private int status;
    private BITalinoDevice device;
    private StreamConnection connection;

    private boolean enabledAcquisition;
    //private int samplingWindowSize = 1000;

    // interface
    private JTabbedPane tabbedPane;
    private JList availableDevices;
    private JButton btn_connect;
    private JButton btn_refresh_devices;

    private JCheckBox chkbox_emg;
    private JCheckBox chkbox_eda;
    private JCheckBox chkbox_ecg;
    private JCheckBox chkbox_acc;
    private JCheckBox chkbox_lux;

    private int[] inputs;
    private int samplingFreq;

    private TimeSeries[] ts;

    public Ydunn(){

        this.setTitle("Ydunn - the open Biosignal collector");
        this.setResizable(true);
        this.setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        this.setPreferredSize(new Dimension(800, 600));
        this.setMinimumSize(new Dimension(800, 600));

        this.addWindowListener(this);

        // set appearance
        this.setBackground(Color.lightGray);
        JPanel topPanel = new JPanel();
        JScrollPane scroll = new JScrollPane(topPanel,
                                                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                                                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        this.getContentPane().add(scroll, BorderLayout.CENTER);

        tabbedPane = new JTabbedPane();
        tabbedPane.setPreferredSize(new Dimension(770,540));
        tabbedPane.addTab( "Connect", buildConnectionDetails() );

        ts = new TimeSeries[5];
        for(int i=0; i < 5; i++){
            ts[i] = new TimeSeries( "timeserie "+i );
            //ts[i].add(new Second(), new Double( 0 ) );
            ts[i].setMaximumItemAge(1000000);
        }
        tabbedPane.addTab( "EMG", createChart("EMG data", "mV", ts[0]) );
        tabbedPane.addTab( "EDA", createChart("EDA data", "mV", ts[1]) );
        tabbedPane.addTab( "ECG", createChart("ECG data", "mV", ts[2]) );
        tabbedPane.addTab( "ACC", createChart("ACC data", "g", ts[3]) );
        tabbedPane.addTab( "LUX", createChart("LUX data", "lux", ts[4]) );

        topPanel.add( tabbedPane, BorderLayout.CENTER );

        this.updateStatus(0);

        this.pack();
    }

    private ChartPanel createChart(String title, String ylabel, TimeSeries series  )
    {
        return new ChartPanel(  ChartFactory.createTimeSeriesChart(
                                title,
                                "Time",
                                ylabel,
                                new TimeSeriesCollection(series),
                                false,
                                false,
                                false));
    }

    private JPanel buildConnectionDetails(){
        JPanel connection_details = new JPanel();

        connection_details.setLayout(new BoxLayout(connection_details, BoxLayout.Y_AXIS));
        connection_details.setPreferredSize(new Dimension(760, 550));

        availableDevices = new JList(new String[0]);
        availableDevices.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        availableDevices.setLayoutOrientation(JList.VERTICAL);
        availableDevices.setVisibleRowCount(-1);
        JScrollPane device_list_scroller = new JScrollPane(availableDevices);

        TitledBorder title = BorderFactory.createTitledBorder("Available Bluetooth devices:");
        title.setTitleJustification(TitledBorder.CENTER);
        availableDevices.setBorder( title );
        availableDevices.setLayout(new BoxLayout(availableDevices, BoxLayout.PAGE_AXIS));
        availableDevices.setMinimumSize(new Dimension(750, 500));
        availableDevices.setMaximumSize(new Dimension(750, 500));
        availableDevices.setAlignmentX(Component.CENTER_ALIGNMENT);
        connection_details.add(device_list_scroller);

        JPanel panel_input_selection = new JPanel();
        panel_input_selection.setLayout(new GridLayout(5, 2));
        panel_input_selection.setMaximumSize(new Dimension(70, 200));
        chkbox_emg = new JCheckBox(); panel_input_selection.add(chkbox_emg); panel_input_selection.add(new JLabel("EMG"));
        chkbox_eda = new JCheckBox(); panel_input_selection.add(chkbox_eda); panel_input_selection.add(new JLabel("EDA"));
        chkbox_ecg = new JCheckBox(); panel_input_selection.add(chkbox_ecg); panel_input_selection.add(new JLabel("ECG"));
        chkbox_acc = new JCheckBox(); panel_input_selection.add(chkbox_acc); panel_input_selection.add(new JLabel("ACC"));
        chkbox_lux = new JCheckBox(); panel_input_selection.add(chkbox_lux); panel_input_selection.add(new JLabel("LUX"));
        connection_details.add(panel_input_selection);

        JPanel conn_button_panel = new JPanel();
        conn_button_panel.setLayout(new BoxLayout(conn_button_panel, BoxLayout.X_AXIS));
        conn_button_panel.setAlignmentX(Component.CENTER_ALIGNMENT);
        connection_details.add(conn_button_panel);

        btn_refresh_devices = new JButton("Refresh");
        btn_refresh_devices.setPreferredSize(new Dimension(150, 60));
        btn_refresh_devices.addActionListener(this);

        btn_connect = new JButton("Connect");
        btn_connect.setPreferredSize(new Dimension(150,60));
        btn_connect.addActionListener(this);

        conn_button_panel.add(btn_refresh_devices);
        conn_button_panel.add(btn_connect);

        return connection_details;
    }

    private void connectTo(String mac, int samplerate, int[] inputs) throws IOException, BITalinoException {
        this.device = new BITalinoDevice(samplerate, inputs);
        // connect to BITalino device
        this.connection = (StreamConnection) Connector.open("btspp://" + mac + ":1", Connector.READ_WRITE);
        this.device.open(connection.openInputStream(), connection.openOutputStream());
    }

    private String getDeviceVersion() throws BITalinoException {
        return this.device.version();
    }

    final Object inquiryCompletedEvent = new Object();
    public static final Vector<RemoteDevice> devicesDiscovered = new Vector<RemoteDevice>();

    private void discoverDevices() throws BluetoothStateException, InterruptedException {

        devicesDiscovered.clear();

        synchronized(inquiryCompletedEvent) {
            boolean started = LocalDevice.getLocalDevice().getDiscoveryAgent().startInquiry(DiscoveryAgent.GIAC, this);
            if (started) {
                System.out.println("wait for device inquiry to complete...");

                inquiryCompletedEvent.wait();

                // update interface with the found devices
                String[] items = new String[devicesDiscovered.size()];
                for(int i=0; i<items.length;i++){
                    try{
                        items[i] = devicesDiscovered.get(i).getFriendlyName(false);
                    }catch (IOException cantGetDeviceName){
                        items[i] = devicesDiscovered.get(i).getBluetoothAddress();
                    }
                }
                this.availableDevices.setListData(items);
            }
        }

    }

    private void trigger(int[] statuses){
        // trigger digital outputs
        // int[] digital = { 1, 1, 1, 1 };
        //this.device.trigger(statuses);
    }

    private void start(){
        // start acquisition on predefined analog channels
        (new Thread(this)).start();
    }

    public void stop(){
        enabledAcquisition = false;


        updateStatus(0);
    }

    private void updateStatus(int status){
        switch(status){
            case 0:
                this.status = 0;
                tabbedPane.setSelectedIndex(0);
                tabbedPane.setEnabledAt(0, true);
                for(int i= 1; i<tabbedPane.getTabCount();i++) tabbedPane.setEnabledAt(i, false);
                break;

            case 1:
                this.status = 1;
                tabbedPane.setSelectedIndex(inputs[0]+1); // enable smallest input selected
                tabbedPane.setEnabledAt(0, false);

                for(Integer i : inputs) System.out.println(i);
                for(int i= 1; i<tabbedPane.getTabCount();i++) {
                    boolean contains = false;
                    for (int j = 0; !contains && j < inputs.length; j++) {
                        if (i-1 == inputs[j]) {
                            tabbedPane.setEnabledAt(i, true);
                            contains = true;
                        }
                    }
                    //if(!contains)  tabbedPane.setEnabledAt(i, false); // already false
                }
                break;
        }
    }

    @Override
    public void run() {

        try {
            device.start();

            // read n samples, according to the selected sampling frequency
            enabledAcquisition = true;
            BITalinoFrame[] samplesRead = new BITalinoFrame[samplingFreq];

            while(enabledAcquisition){

                System.out.println("Reading " + samplingFreq + " samples..");

                //Date now = Calendar.getInstance().getTime();
                Millisecond p = new Millisecond();

                for (int counter = 0; counter < samplingFreq; counter++) {

                    BITalinoFrame[] frames = device.read(1);
                    samplesRead[counter] = frames[0];

                    //System.out.println("FRAME: " + frames[0].toString());
                    for(Integer i : inputs){
                        // 1000 Hz = 1ms, 100 Hz = 10 ms, 10 Hz = 100 ms
                        ts[i].addOrUpdate(p.next(), samplesRead[counter].getAnalog(i));
                    }

                }
            }

            // stop acquisition and close bluetooth connection
            this.device.stop();
        } catch (BITalinoException e) {
            e.printStackTrace();
        } /*catch (InterruptedException e) {
            e.printStackTrace();
        }*/
    }

    public static void main(String[] args) throws Throwable {
        java.awt.EventQueue.invokeLater(new Runnable() {
            public void run() {
                Ydunn frame = new Ydunn();
                frame.setVisible(true);
            }
        });
    }


    @Override
    public void actionPerformed(ActionEvent actionEvent) {
        switch(actionEvent.getActionCommand()){
            case "Refresh":
                try {
                    discoverDevices();
                } catch (BluetoothStateException e) {
                    JOptionPane.showMessageDialog(this, e.getMessage());
                } catch (InterruptedException e) {
                    JOptionPane.showMessageDialog(this, e.getMessage());
                }
                break;

            case "Connect":
                if(this.availableDevices.getSelectedIndex() == -1){
                    JOptionPane.showMessageDialog(this, "No device selected!!");
                }else {
                    try {
                        //mapping = [('EMG',1000), ('EDA',10), ('ECG',100), ('ACC',100), ('LUX',10)]
                        samplingFreq = 10;
                        if(chkbox_emg.isSelected()) samplingFreq = 1000;
                        else if(chkbox_ecg.isSelected() || chkbox_acc.isSelected()) samplingFreq = 100;

                        ArrayList<Integer> ins = new ArrayList<>();
                        if(chkbox_emg.isSelected()) ins.add(0);
                        if(chkbox_eda.isSelected()) ins.add(1);
                        if(chkbox_ecg.isSelected()) ins.add(2);
                        if(chkbox_acc.isSelected()) ins.add(3);
                        if(chkbox_lux.isSelected()) ins.add(4);
                        inputs = ins.stream().mapToInt(i->i).toArray();   // God bless Lambda functions!

                        if(inputs.length > 0){

                            this.connectTo(devicesDiscovered.get(this.availableDevices.getSelectedIndex()).getBluetoothAddress(),
                                    samplingFreq,
                                    inputs);

                            this.start();
                            this.updateStatus(1);
                        }else{
                            JOptionPane.showMessageDialog(this, "You must select at least 1 channel to read data from");
                        }

                    } catch (Exception e) {
                        JOptionPane.showMessageDialog(this, e.getMessage());
                    }
                }
                break;
        }
    }

    @Override
    public void windowOpened(WindowEvent windowEvent) {

    }

    @Override
    public void windowClosing(WindowEvent windowEvent) {

    }

    @Override
    public void windowClosed(WindowEvent windowEvent) {
        if(status > 0){
            //try {
                enabledAcquisition = false;
                //device.stop();
            //} catch (BITalinoException e) {
             //   e.printStackTrace();
            //}
        }
    }

    @Override
    public void windowIconified(WindowEvent windowEvent) {

    }

    @Override
    public void windowDeiconified(WindowEvent windowEvent) {

    }

    @Override
    public void windowActivated(WindowEvent windowEvent) {

    }

    @Override
    public void windowDeactivated(WindowEvent windowEvent) {

    }

    @Override
    public void deviceDiscovered(RemoteDevice remoteDevice, DeviceClass deviceClass) {
        devicesDiscovered.addElement(remoteDevice);
    }

    @Override
    public void servicesDiscovered(int i, ServiceRecord[] serviceRecords) {

    }

    @Override
    public void serviceSearchCompleted(int i, int i1) {

    }

    @Override
    public void inquiryCompleted(int i) {
        synchronized(inquiryCompletedEvent){
            inquiryCompletedEvent.notifyAll();
        }
    }
}
