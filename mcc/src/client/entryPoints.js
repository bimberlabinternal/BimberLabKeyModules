module.exports = {
  apps: [{
    name: 'dashboard',
    title: 'MCC Data',
    permission: 'read',
    path: './src/client/Dashboard'
  },{
    name: 'dashboardWebpart',
    title: 'MCC Data',
    permission: 'read',
    path: './src/client/Dashboard/webpart',
    generateLib: true
  }]
};
