module.exports = {
  apps: [{
    name: 'dashboard',
    title: 'Dashboard Page',
    permission: 'read',
    path: './src/client/Dashboard'
  },{
    name: 'dashboardWebpart',
    title: 'Dashboard Webpart',
    permission: 'read',
    path: './src/client/Dashboard/webpart',
    generateLib: true
  }]
};
