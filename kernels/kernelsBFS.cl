__kernel void kernelBFS(global const int* g_graph_nodes_noe, global const int* g_graph_nodes_start,global const int* g_graph_edges,global int* g_graph_mask,global int* g_updating_graph_mask,global int* g_graph_visited,global int* g_cost,global int* no_of_nodes,global int* MAX_WIPG)
{
	int tid = get_global_id(0);
	if( tid < no_of_nodes[0] && g_graph_mask[tid]!=0)
	{
		g_graph_mask[tid]=0;
		for(int i=g_graph_nodes_start[tid]; i<(g_graph_nodes_noe[tid] + g_graph_nodes_start[tid]); i++)
			{
			int id = g_graph_edges[i];
			if(g_graph_visited[id]==0)
				{
				g_cost[id]=g_cost[tid]+1;
				g_updating_graph_mask[id]=1;
				}
			}
	}
	
}

__kernel void kernelBFS2( global int* g_graph_mask, global int *g_updating_graph_mask, global int* g_graph_visited, global int *g_over, global int* no_of_nodes, global int* MAX_WIPG)
{
	int tid = get_global_id(0);
	if( tid<no_of_nodes[0] && g_updating_graph_mask[tid]!=0)
	{

		g_graph_mask[tid]=1;
		g_graph_visited[tid]=1;
		g_over[0]=1;
		g_updating_graph_mask[tid]=0;
	}
}